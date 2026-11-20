/*
 * Universal Password Manager
 * Copyright (c) 2010-2011 Adrian Smith
 *
 * This file is part of Universal Password Manager.
 *
 * Universal Password Manager is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Universal Password Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Universal Password Manager; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.u17od.upm.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Database format version 4 - byte-for-byte identical to the desktop client.
 *
 * Key derivation:  Argon2id (BouncyCastle lightweight API, the same classes
 *                  used by the desktop client) over the UTF-8 encoding of the
 *                  master password, producing a 256 bit key.
 * Encryption:      AES-256-GCM (authenticated encryption), fresh random 96 bit
 *                  IV for every save.
 *
 * File header (all in the clear):
 *   "UPM" | 0x04 | salt(16) | iterations(4,BE) | memoryKB(4,BE) | parallelism(1) | IV(12)+ciphertext+tag(16)
 */
public class EncryptionServiceV4 extends EncryptionService {

    public static final int SALT_LENGTH_V4 = 16;
    public static final int IV_LENGTH      = 12;
    public static final int TAG_BITS       = 128;
    public static final int KEY_BYTES      = 32;

    public static final int DEFAULT_ITERATIONS  = 3;
    public static final int DEFAULT_MEMORY_KB   = 32768; // 32 MiB
    public static final int DEFAULT_PARALLELISM = 2;

    private static final String CIPHER_NAME = "AES/GCM/NoPadding";
    private static final String RANDOM_NAME = "SHA1PRNG";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private char[] password;
    private byte[] salt;
    private int iterations;
    private int memoryKB;
    private int parallelism;

    private byte[] cachedKey;
    private byte[] cachedKeySalt;
    private char[] cachedKeyPassword;


    public EncryptionServiceV4(char[] password) {
        this(password, generateSalt(), DEFAULT_ITERATIONS, DEFAULT_MEMORY_KB, DEFAULT_PARALLELISM);
    }


    public EncryptionServiceV4(char[] password, byte[] salt, int iterations, int memoryKB, int parallelism) {
        this.password = password.clone();
        this.salt = salt;
        this.iterations = iterations;
        this.memoryKB = memoryKB;
        this.parallelism = parallelism;
    }


    private static byte[] generateSalt() {
        try {
            SecureRandom saltGen = SecureRandom.getInstance(RANDOM_NAME);
            byte[] pSalt = new byte[SALT_LENGTH_V4];
            saltGen.nextBytes(pSalt);
            return pSalt;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }


    private byte[] passwordBytes() {
        CharBuffer cb = CharBuffer.wrap(password);
        ByteBuffer bb = UTF8.encode(cb);
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }


    private byte[] deriveKey() throws GeneralSecurityException {
        // Argon2 is deliberately slow, so cache the key until password/salt change
        if (cachedKey != null
                && Arrays.equals(cachedKeySalt, salt)
                && Arrays.equals(cachedKeyPassword, password)) {
            return cachedKey;
        }
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKB)
                .withParallelism(parallelism);
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        cachedKey = new byte[KEY_BYTES];
        generator.generateBytes(passwordBytes(), cachedKey);
        cachedKeySalt = salt.clone();
        cachedKeyPassword = password.clone();
        return cachedKey;
    }


    /**
     * Re-derives everything for a different salt/parameters (e.g. when a
     * downloaded remote database has its own salt). Invalidates the key cache.
     */
    public void reconfigure(byte[] newSalt, int newIterations, int newMemoryKB, int newParallelism) {
        this.salt = newSalt;
        this.iterations = newIterations;
        this.memoryKB = newMemoryKB;
        this.parallelism = newParallelism;
        this.cachedKey = null;
    }


    public byte[] encrypt(byte[] cleartext) throws IllegalBlockSizeException, BadPaddingException {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom rng = SecureRandom.getInstance(RANDOM_NAME);
            rng.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(cleartext);

            byte[] out = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalBlockSizeException(e.getMessage());
        }
    }


    public byte[] decrypt(byte[] ciphertext) throws IllegalBlockSizeException, InvalidPasswordException {
        if (ciphertext.length <= IV_LENGTH) {
            throw new InvalidPasswordException();
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_LENGTH));
            return cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.length - IV_LENGTH);
        } catch (AEADBadTagException e) {
            // GCM tag mismatch: wrong password or tampered/corrupted file
            throw new InvalidPasswordException();
        } catch (GeneralSecurityException e) {
            throw new InvalidPasswordException();
        }
    }


    /**
     * The master password this service derives its Argon2id key from.
     * Must override the parent accessor: this class shadows the parent's
     * password field with its own, so without this getPassword() would
     * return the parent's (unset) null field and sync would wrongly fall
     * back to the SecretKey path, which cannot open v4 files.
     */
    @Override
    public char[] getPassword() {
        return password.clone();
    }


    public byte[] getSalt() {
        return salt;
    }


    public int getIterations() {
        return iterations;
    }


    public int getMemoryKB() {
        return memoryKB;
    }


    public int getParallelism() {
        return parallelism;
    }

}
