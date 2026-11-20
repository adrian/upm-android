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
package com.u17od.upm.database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import com.u17od.upm.crypto.DESDecryptionService;
import com.u17od.upm.crypto.EncryptionService;
import com.u17od.upm.crypto.EncryptionServiceV4;
import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.util.Util;


/**
 * This class represents the main interface to a password database.
 * All interaction with the database file is done using this class.
 * 
 * Database versions and formats. The items between [] brackets are encrypted.
 *   4     >> MAGIC_NUMBER DB_VERSION SALT KDF_PARAMS [IV DB_REVISION DB_OPTIONS ACCOUNTS]
 *            (Argon2id KDF: SALT=16 bytes, KDF_PARAMS=iterations(4,BE) memoryKB(4,BE) parallelism(1);
 *             AES-256-GCM, the 12 byte IV is stored in the clear at the start of the encrypted data)
 *   3     >> MAGIC_NUMBER DB_VERSION SALT [DB_REVISION DB_OPTIONS ACCOUNTS]
 *      (all strings are encoded using UTF-8)
 *   2     >> MAGIC_NUMBER DB_VERSION SALT [DB_REVISION DB_OPTIONS ACCOUNTS]
 *   1.1.0 >> SALT [DB_HEADER DB_REVISION DB_OPTIONS ACCOUNTS]
 *   1.0.0 >> SALT [DB_HEADER ACCOUNTS]
 * 
 *   DB_VERSION = The structural version of the database
 *   SALT = The salt used to mix with the user password to create the key
 *   DB_HEADER = Was used to store the structural version of the database (pre version 2)
 *   DB_OPTIONS = Options relating to the database
 *   ACCOUNTS = The account information
 *   
 *   From version 2 the db version is stored unencrypted at the start of the file.
 *   This allows for cryptographic changes in the database structure because beforehand
 *   we had to know how to unencrypt the database before we could find out the version number.
 */
public class PasswordDatabase {

    private static final int DB_VERSION_V3 = 3;
    private static final int DB_VERSION_V4 = 4;
    private static final String FILE_HEADER = "UPM";

    private File databaseFile;
    private Revision revision;
    private DatabaseOptions dbOptions;
    private HashMap<String, AccountInformation> accounts;
    private EncryptionService encryptionService;


    public PasswordDatabase(File dbFile, SecretKey secretKey) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {
        databaseFile = dbFile;
        load(secretKey);
    }


    public PasswordDatabase(File dbFile, char[] password) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {
        this(dbFile, password, false);
    }


    public PasswordDatabase(File dbFile, char[] password, boolean overwrite) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {
        databaseFile = dbFile;
        //Either create a new file (if it exists and overwrite == true OR it doesn't exist) or open the existing file
        if ((databaseFile.exists() && overwrite == true) || !databaseFile.exists()) {
            databaseFile.delete();
            databaseFile.createNewFile();
            revision = new Revision();
            dbOptions = new DatabaseOptions();
            accounts = new HashMap<String, AccountInformation>();
            encryptionService = new EncryptionServiceV4(password);
        } else {
            load(password);
        }
    }


    public void changePassword(char[] password) {
        encryptionService = new EncryptionServiceV4(password);
    }


    private void load(char[] password) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {
        loadInternal(Util.getBytesFromFile(databaseFile), password, EncryptionService.createSecretKey(password));
    }


    private void load(SecretKey secretKey) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {
        loadInternal(Util.getBytesFromFile(databaseFile), null, secretKey);
    }


    private void loadInternal(byte[] fullDatabase, char[] password, SecretKey secretKey) throws IOException, GeneralSecurityException, ProblemReadingDatabaseFile, InvalidPasswordException {

        // Check the database is a minimum length
        if (fullDatabase.length < EncryptionService.SALT_LENGTH) {
            throw new ProblemReadingDatabaseFile("This file doesn't appear to be a UPM password database");
        }

        ByteArrayInputStream is = null;
        Charset charset = Charset.forName("UTF-8");

        // Ensure this is a real UPM database by checking for the existance of the string "UPM" at the start of the file
        byte[] header = new byte[FILE_HEADER.getBytes().length];
        System.arraycopy(fullDatabase, 0, header, 0, header.length);
        if (Arrays.equals(header, FILE_HEADER.getBytes())) {

            // Calculate the positions of each item in the file
            int dbVersionPos = header.length;
            int saltPos      = dbVersionPos + 1;

            // Get the database version
            byte dbVersion = fullDatabase[dbVersionPos];

            if (dbVersion == 4) {
                if (password == null) {
                    throw new ProblemReadingDatabaseFile("This database uses encryption format v4 and needs the master password to be opened");
                }
                int paramsPos  = saltPos + EncryptionServiceV4.SALT_LENGTH_V4;
                int payloadPos = paramsPos + 9; // iterations(4) + memoryKB(4) + parallelism(1)
                if (fullDatabase.length < payloadPos + EncryptionServiceV4.IV_LENGTH + (EncryptionServiceV4.TAG_BITS / 8)) {
                    throw new ProblemReadingDatabaseFile("This file doesn't appear to be a UPM password database");
                }
                byte[] salt = new byte[EncryptionServiceV4.SALT_LENGTH_V4];
                System.arraycopy(fullDatabase, saltPos, salt, 0, EncryptionServiceV4.SALT_LENGTH_V4);
                int iterations  = readBE(fullDatabase, paramsPos);
                int memoryKB    = readBE(fullDatabase, paramsPos + 4);
                int parallelism = fullDatabase[paramsPos + 8] & 0xFF;
                int encryptedBytesLength = fullDatabase.length - payloadPos;
                byte[] encryptedBytes = new byte[encryptedBytesLength];
                System.arraycopy(fullDatabase, payloadPos, encryptedBytes, 0, encryptedBytesLength);

                //Attempt to decrypt the database information
                EncryptionServiceV4 v4Service = new EncryptionServiceV4(password, salt, iterations, memoryKB, parallelism);
                byte[] decryptedBytes = v4Service.decrypt(encryptedBytes);
                encryptionService = v4Service;

                //If we've got here then the database was successfully decrypted
                is = new ByteArrayInputStream(decryptedBytes);
                revision = new Revision(is);
                dbOptions = new DatabaseOptions(is);
            } else if (dbVersion == 2 || dbVersion == 3) {
                int encryptedBytesPos = saltPos + EncryptionService.SALT_LENGTH;
                byte[] salt = new byte[EncryptionService.SALT_LENGTH];
                System.arraycopy(fullDatabase, saltPos, salt, 0, EncryptionService.SALT_LENGTH);
                int encryptedBytesLength = fullDatabase.length - encryptedBytesPos;
                byte[] encryptedBytes = new byte[encryptedBytesLength]; 
                System.arraycopy(fullDatabase, encryptedBytesPos, encryptedBytes, 0, encryptedBytesLength);

                // From version 3 onwards Strings in AccountInformation are
                // encoded using UTF-8. To ensure we can still open older dbs
                // we default back to the then character set, the system default
                if (dbVersion < 3) {
                    charset = Util.defaultCharset();
                }

                //Attempt to decrypt the database information. When we have the
                //password we build a password based service so that save() can
                //upgrade the database to format v4.
                if (password != null) {
                    encryptionService = new EncryptionService(password, salt);
                } else {
                    encryptionService = new EncryptionService(secretKey, salt);
                }
                byte[] decryptedBytes = encryptionService.decrypt(encryptedBytes);

                //If we've got here then the database was successfully decrypted 
                is = new ByteArrayInputStream(decryptedBytes);
                revision = new Revision(is);
                dbOptions = new DatabaseOptions(is);
            } else {
                throw new ProblemReadingDatabaseFile("Don't know how to handle database version [" + dbVersion + "]");
            }

        } else {
            
            // This might be an old database (pre version 2) so try loading it using the old database format
            
            // Check the database is a minimum length
            if (fullDatabase.length < EncryptionService.SALT_LENGTH) {
                throw new ProblemReadingDatabaseFile("This file doesn't appear to be a UPM password database");
            }
            
            //Split up the salt and encrypted bytes
            byte[] salt = new byte[EncryptionService.SALT_LENGTH];
            System.arraycopy(fullDatabase, 0, salt, 0, EncryptionService.SALT_LENGTH);
            int encryptedBytesLength = fullDatabase.length - EncryptionService.SALT_LENGTH;
            byte[] encryptedBytes = new byte[encryptedBytesLength]; 
            System.arraycopy(fullDatabase, EncryptionService.SALT_LENGTH, encryptedBytes, 0, encryptedBytesLength);

            byte[] decryptedBytes = null;
            try {
                //Attempt to decrypt the database information
                decryptedBytes = DESDecryptionService.decrypt(secretKey, salt, encryptedBytes);
            } catch (IllegalBlockSizeException e) {
                throw new ProblemReadingDatabaseFile("Either your password is incorrect or this file isn't a UPM password database");
            }

            // Create the encryption for use later in the save() method. If we
            // have the password, switch straight to a version 4 service so the
            // next save() migrates this legacy database to the new format.
            if (password != null) {
                encryptionService = new EncryptionServiceV4(password);
            } else {
                encryptionService = new EncryptionService(secretKey, salt);
            }
            
            //We'll get to here if the password was correct so load up the decryped byte
            is = new ByteArrayInputStream(decryptedBytes);
            DatabaseHeader dh = new DatabaseHeader(is);

            // At this point we'll check to see what version the database is and load it accordingly
            if (dh.getVersion().equals("1.1.0")) {
                // Version 1.1.0 introduced a revision number & database options so read that in now
                revision = new Revision(is);
                dbOptions = new DatabaseOptions(is);
            } else if (dh.getVersion().equals("1.0.0")) {
                revision = new Revision();
                dbOptions = new DatabaseOptions();
            } else {
                throw new ProblemReadingDatabaseFile("Don't know how to handle database version [" + dh.getVersion() + "]");
            }

        }
        
        // Read the remainder of the database in now
        accounts = new HashMap<String, AccountInformation>();
        try {
            while (true) { //keep loading accounts until an EOFException is thrown
                AccountInformation ai = new AccountInformation(is, charset);
                addAccount(ai);
            }
        } catch (EOFException e) {
            //just means we hit eof
        }
        is.close();
        
    }
    

    public void addAccount(AccountInformation ai) {
        accounts.put(ai.getAccountName(), ai);
    }
    

    public void deleteAccount(String accountName) {
        accounts.remove(accountName);
    }

    
    public AccountInformation getAccount(String name) {
        return accounts.get(name);
    }
    
    
    public void save() throws IOException, IllegalBlockSizeException, BadPaddingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        
        // Flatpack the database revision and options
        revision.increment();
        revision.flatPack(os);
        dbOptions.flatPack(os);

        // Flatpack the accounts
        Iterator<AccountInformation> it = accounts.values().iterator();
        while (it.hasNext()) {
            AccountInformation ai = it.next();
            ai.flatPack(os);
        }
        os.close();
        byte[] dataToEncrypt = os.toByteArray();

        //Upgrade an older database to the v4 format whenever the master
        //password is available (databases opened with a SecretKey stay on v3)
        if (!(encryptionService instanceof EncryptionServiceV4) && encryptionService.getPassword() != null) {
            encryptionService = new EncryptionServiceV4(encryptionService.getPassword());
        }

        //Now encrypt the database data
        byte[] encryptedData = encryptionService.encrypt(dataToEncrypt);

        // Write the salt and the encrypted data out to a temporary file
        File tempFile = File.createTempFile("upmdb", null);
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(FILE_HEADER.getBytes());
        if (encryptionService instanceof EncryptionServiceV4) {
            EncryptionServiceV4 v4 = (EncryptionServiceV4) encryptionService;
            fos.write(DB_VERSION_V4);
            fos.write(v4.getSalt());
            fos.write(intBE(v4.getIterations()));
            fos.write(intBE(v4.getMemoryKB()));
            fos.write((byte) v4.getParallelism());
        } else {
            fos.write(DB_VERSION_V3);
            fos.write(encryptionService.getSalt());
        }
        fos.write(encryptedData);
        fos.close();

        // Rename the tempfile to the real database file
        // The reason for this is to protect against the write thread being
        // terminated thus corrupting the file.
        tempFile.renameTo(databaseFile);
    }

    
    public ArrayList<AccountInformation> getAccounts() {
        return new ArrayList<AccountInformation>(accounts.values());
    }
    
    
    public ArrayList<String> getAccountNames() {
        ArrayList<String> accountNames = new ArrayList<String>(accounts.keySet());
        Collections.sort(accountNames, String.CASE_INSENSITIVE_ORDER);
        return accountNames;
    }


    public File getDatabaseFile() {
        return databaseFile;
    }


    /**
     * There are times when we decrypt a temp version of the database file,
     * e.g. when we download a db during sync. If we end up making this temp db
     * our permanent db then we don't want to have to decrypt it again. In this
     * instance what we do is overwrite the main db file with the temp downloaded
     * one and then repoint this PassswordDatabase at the main db file.  
     * @param file
     */
    public void setDatabaseFile(File file) {
        databaseFile = file;
    }


    public DatabaseOptions getDbOptions() {
        return dbOptions;
    }


    public int getRevision() {
        return revision.getRevision();
    }


    /**
     * Check if the given bytes represent a password database by examining the
     * header bytes for the UPM magic number.
     * @param data
     * @return
     */
    public static boolean isPasswordDatabase(byte[] data) {
        boolean isPasswordDatabase = false;

        // Extract the header bytes
        byte[] headerBytes = new byte[FILE_HEADER.getBytes().length];
        if (data != null && data.length > headerBytes.length) {
            // Check if the first n bytes are what we expect in a UPM password
            // database
            for (int i=0; i<headerBytes.length; i++) {
                headerBytes[i] = data[i];
            }
            if (Arrays.equals(headerBytes, FILE_HEADER.getBytes())) {
                isPasswordDatabase = true;
            }
        }

        return isPasswordDatabase;
    }

    public static boolean isPasswordDatabase(File file) throws IOException {
        boolean isPasswordDatabase = false;

        // Extract the header bytes
        byte[] headerBytes = new byte[FILE_HEADER.getBytes().length];
        if (file != null && file.length() > headerBytes.length) {
            byte[] data = Util.getBytesFromFile(file, headerBytes.length + 1);
            return isPasswordDatabase(data);
        }

        return isPasswordDatabase;
    }

    public EncryptionService getEncryptionService () {
        return encryptionService;
    }


    private static int readBE(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }


    private static byte[] intBE(int value) {
        return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
    }

}
