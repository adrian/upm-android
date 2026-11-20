package com.u17od.upm.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.database.ProblemReadingDatabaseFile;

/**
 * Cross-client test for database format v4. Opens golden_v4.db, a database
 * created by the hardened *desktop* client (Argon2id + AES-256-GCM), using
 * this Android code base. If this test passes the two clients are byte for
 * byte compatible and can share the same upm.db over the network.
 *
 * The golden file lives in tests/resources/golden_v4.db.
 */
public class DatabaseFormatV4CrossTest {

    //same master password the golden file was created with
    private static final char[] PASSWORD = "m\u00e4ster-P\u00e4ss-\u20ac123".toCharArray();

    @BeforeClass
    public static void installProvider() {
        //on a device the platform already registers the BC provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static byte[] readGolden() throws Exception {
        InputStream is = DatabaseFormatV4CrossTest.class.getResourceAsStream("/golden_v4.db");
        if (is == null) {
            //fallback if test resources are not packaged: relative to module dir
            is = new FileInputStream(new File("tests/resources/golden_v4.db"));
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    }

    private File copyGoldenToTempDir() throws Exception {
        byte[] data = readGolden();
        File dir = Files.createTempDirectory("upm-golden").toFile();
        dir.deleteOnExit();
        File f = new File(dir, "golden.db");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data);
        fos.close();
        return f;
    }

    @Test
    public void headerMatchesSharedV4Spec() throws Exception {
        byte[] b = readGolden();
        assertEquals('U', b[0]);
        assertEquals('P', b[1]);
        assertEquals('M', b[2]);
        assertEquals(4, b[3]);
        //salt(16) then iterations(4) memoryKB(4) parallelism(1)
        assertEquals(3, readBE(b, 4 + 16));
        assertEquals(32768, readBE(b, 4 + 16 + 4));
        assertEquals(2, b[4 + 16 + 8] & 0xFF);
    }

    @Test
    public void openDesktopCreatedV4Database() throws Exception {
        File f = copyGoldenToTempDir();
        PasswordDatabase db = new PasswordDatabase(f, PASSWORD);
        assertEquals(2, db.getAccounts().size());
        AccountInformation ai = db.getAccount("Test Account");
        assertNotNull(ai);
        assertEquals("user-\u20ac", ai.getUserId());
        assertEquals("p@ss/\u20ac", ai.getPassword());
        assertEquals("http://example.com/\u20ac", ai.getUrl());
        assertEquals("note \u00e8\u00e0\u00fc\u20ac", ai.getNotes());
        assertNotNull(db.getAccount("Second Account"));
    }

    @Test
    public void saveStaysInV4AndRoundTrips() throws Exception {
        File f = copyGoldenToTempDir();
        PasswordDatabase db = new PasswordDatabase(f, PASSWORD);
        int revBefore = db.getRevision();

        //write to a fresh file: File.renameTo can't replace existing files on
        //every platform (e.g. Windows) which is what save() uses internally
        File out = new File(f.getParentFile(), "resaved.db");
        db.setDatabaseFile(out);
        db.save();

        byte[] b = Files.readAllBytes(out.toPath());
        assertEquals('U', b[0]);
        assertEquals(4, b[3]);
        assertEquals(3, readBE(b, 4 + 16));
        assertEquals(32768, readBE(b, 4 + 16 + 4));
        assertEquals(2, b[4 + 16 + 8] & 0xFF);

        PasswordDatabase db2 = new PasswordDatabase(out, PASSWORD);
        assertEquals(2, db2.getAccounts().size());
        assertEquals("user-\u20ac", db2.getAccount("Test Account").getUserId());
        assertEquals(revBefore + 1, db2.getRevision());
    }

    @Test
    public void wrongPasswordRejected() throws Exception {
        File f = copyGoldenToTempDir();
        try {
            new PasswordDatabase(f, "wrong-password".toCharArray());
            fail("wrong password was accepted");
        } catch (InvalidPasswordException e) {
            //expected: GCM tag mismatch
        }
    }

    @Test
    public void tamperedFileRejected() throws Exception {
        File f = copyGoldenToTempDir();
        byte[] b = Files.readAllBytes(f.toPath());
        b[b.length - 5] ^= 0x01; //flip a bit inside the GCM tag
        Files.write(f.toPath(), b);
        try {
            new PasswordDatabase(f, PASSWORD);
            fail("tampered database was accepted");
        } catch (InvalidPasswordException e) {
            //expected
        }
    }

    @Test
    public void v4ServiceKeepsPasswordForSync() throws Exception {
        // Regression test: EncryptionServiceV4 shadows the parent's password
        // field. If getPassword() is not overridden it returns null and
        // SyncDatabaseActivity.decryptDatabase() falls into the SecretKey
        // branch, which cannot open a v4 remote database.
        File localCopy = copyGoldenToTempDir();
        PasswordDatabase localDb = new PasswordDatabase(localCopy, PASSWORD);

        EncryptionService service = localDb.getEncryptionService();
        assertNotNull("v4 service must expose the master password for sync",
                service.getPassword());
        assertEquals(String.valueOf(PASSWORD), String.valueOf(service.getPassword()));

        // the exact sequence SyncDatabaseActivity.decryptDatabase() runs
        File remoteCopy = copyGoldenToTempDir();
        PasswordDatabase remoteDb = new PasswordDatabase(remoteCopy, service.getPassword());
        assertEquals(2, remoteDb.getAccounts().size());
    }

    @Test
    public void secretKeyPathCannotOpenV4File() throws Exception {
        File f = copyGoldenToTempDir();
        try {
            new PasswordDatabase(f, EncryptionService.createSecretKey(PASSWORD));
            fail("the SecretKey path should not be able to open a v4 database");
        } catch (ProblemReadingDatabaseFile e) {
            //expected: v4 needs the master password
        }
    }

    private static int readBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }
}
