package com.u17od.upm.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.u17od.upm.database.PasswordDatabase;

/**
 * Reproduces the Android "Sync" HTTP request flow against a local server that
 * mimics the QNAP setup: a .htaccess-protected file (401 without Basic auth,
 * like Apache) and a "web UI" style endpoint that answers HTTP 200 with an
 * HTML page instead of the database file.
 *
 * Purpose: pin down which server answer produces which app toast.
 *  - 401 (htaccess working, credentials missing)   -> TransportException
 *                                                    -> "Problem Downloading Database"
 *  - 200 + HTML (wrong endpoint, no auth required) -> bytes fail the "UPM" magic
 *                                                    -> "Not a Password Database"
 * Also proves the sync upload path (delete + put) authenticates the same way,
 * and that credentials embedded in the remote location (user:pass@host) are
 * honoured by the Android client as they are by the desktop client.
 *
 * Written with plain java.net (ServerSocket) because the Android unit test
 * classpath is android.jar, which lacks com.sun.net.httpserver.
 */
public class SyncHttpReproTest {

    private static final String USER = "qnapuser";
    private static final String PASS = "qnappass";
    private static final String EXPECTED_AUTH =
            "Basic " + Base64.getEncoder().encodeToString((USER + ":" + PASS).getBytes(StandardCharsets.UTF_8));
    private static final String HTML = "<html><body>QTS login page</body></html>";

    private static ServerSocket serverSocket;
    private static Thread acceptThread;
    private static volatile boolean running;
    private static int port;
    private static byte[] goldenV4;
    private static File tmpDir;
    private static volatile String lastAuthHeader;

    @BeforeClass
    public static void startServer() throws Exception {
        goldenV4 = readGoldenV4();
        tmpDir = Files.createTempDirectory("upm-sync-test").toFile();

        serverSocket = new ServerSocket(0, 16, java.net.InetAddress.getLoopbackAddress());
        port = serverSocket.getLocalPort();
        running = true;
        acceptThread = new Thread(SyncHttpReproTest::acceptLoop, "sync-repro-server");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        running = false;
        serverSocket.close();
    }

    private static void acceptLoop() {
        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                Thread worker = new Thread(() -> handle(socket));
                worker.setDaemon(true);
                worker.start();
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handle(Socket socket) {
        try (Socket s = socket) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            // Read headers (up to the blank line) as raw bytes so the body stays intact.
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int state = 0; // match trailing \r\n\r\n
            int b;
            while (headerBuf.size() < 64 * 1024) {
                b = in.read();
                if (b == -1) {
                    return;
                }
                headerBuf.write(b);
                if (b == '\r' || b == '\n') {
                    if (b == '\r') {
                        if (state == 0 || state == 2) { state++; } else { state = 0; }
                    } else {
                        if (state == 1 || state == 3) { state++; } else { state = 0; }
                    }
                    if (state == 4) {
                        break;
                    }
                } else {
                    state = 0;
                }
            }

            String headers = new String(headerBuf.toByteArray(), StandardCharsets.ISO_8859_1);
            String requestLine = headers.split("\r\n")[0];
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            String auth = null;
            int contentLength = 0;
            for (String line : headers.split("\r\n")) {
                String lower = line.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("authorization:")) {
                    auth = line.substring("authorization:".length()).trim();
                } else if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            lastAuthHeader = auth;

            // Drain the request body so the client doesn't see a broken pipe.
            int toRead = contentLength;
            byte[] sink = new byte[8192];
            while (toRead > 0) {
                int n = in.read(sink, 0, Math.min(sink.length, toRead));
                if (n == -1) {
                    break;
                }
                toRead -= n;
            }

            boolean protectedPath = path.startsWith("/upm/upm.db")
                    || path.startsWith("/upm/upload.php")
                    || path.startsWith("/upm/deletefile.php");
            if (path.startsWith("/qtsadmin/upm.db")) {
                // QTS admin-UI style endpoint: always 200 + HTML, auth irrelevant.
                respond(out, 200, "text/html", HTML.getBytes(StandardCharsets.UTF_8));
            } else if (protectedPath) {
                if (EXPECTED_AUTH.equals(auth)) {
                    byte[] body = path.endsWith(".db")
                            ? goldenV4
                            : "OK".getBytes(StandardCharsets.UTF_8);
                    respond(out, 200, "application/octet-stream", body);
                } else {
                    respond(out, 401, "Basic realm=\"reserved_area\"", new byte[0]);
                }
            } else {
                respond(out, 404, null, new byte[0]);
            }
            // Silence "unused" on method variable; kept for clarity of the log.
            if (method == null) {
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            // Connection reset etc. during tests is not interesting.
        }
    }

    private static void respond(OutputStream out, int status, String contentTypeOrChallenge, byte[] body) throws Exception {
        StringBuilder sb = new StringBuilder();
        if (status == 200) {
            sb.append("HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: ").append(contentTypeOrChallenge).append("\r\n");
        } else if (status == 401) {
            sb.append("HTTP/1.1 401 Unauthorized\r\n");
            sb.append("WWW-Authenticate: ").append(contentTypeOrChallenge).append("\r\n");
        } else {
            sb.append("HTTP/1.1 404 Not Found\r\n");
        }
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static byte[] readGoldenV4() throws Exception {
        try (InputStream is = SyncHttpReproTest.class.getResourceAsStream("/golden_v4.db")) {
            if (is != null) {
                return readAll(is);
            }
        }
        Path p = Paths.get("tests", "resources", "golden_v4.db");
        return Files.readAllBytes(p);
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static HTTPTransport transport() {
        return new HTTPTransport(new File("no-such-cert-file"), "", tmpDir);
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    // Correct Basic auth (i.e. an "Authentication entry" account set in the db):
    // the sync GET gets the real database bytes.
    @Test
    public void authenticatedSyncDownloadGetsRealDatabase() throws Exception {
        byte[] bytes = transport().get(url("/upm/upm.db"), USER, PASS);
        assertArrayEquals(goldenV4, bytes);
        assertTrue(PasswordDatabase.isPasswordDatabase(bytes));
        assertEquals(EXPECTED_AUTH, lastAuthHeader);
    }

    // No credentials sent (empty Authentication entry, no user:pass@ in the URL)
    // against the htaccess-protected path: Apache answers 401 -> the app shows
    // "Problem Downloading Database", NOT "Not a Password Database".
    @Test
    public void missingCredentialsAgainstHtaccessGives401NotTheMagicToast() {
        try {
            transport().get(url("/upm/upm.db"), null, null);
            fail("expected TransportException on HTTP 401");
        } catch (TransportException expected) {
            // 401 path: get() throws, so no file ever reaches the "UPM" magic check
        }
        assertNull(lastAuthHeader);
    }

    // Wrong endpoint (e.g. remote location pointing at the QTS admin port/UI):
    // HTTP 200 + HTML body -> magic check fails -> this is the exact signature
    // of the "Not a Password Database" toast on sync.
    @Test
    public void htmlPageWithHttp200IsTheNotAPasswordDatabaseSignature() throws Exception {
        byte[] bytes = transport().get(url("/qtsadmin/upm.db"), null, null);
        assertFalse(PasswordDatabase.isPasswordDatabase(bytes));
    }

    // Credentials embedded in the remote location (http://user:pass@host/upm):
    // the Android 1.27.2 client authenticates like the desktop client does.
    @Test
    public void userInfoInTheRemoteLocationAuthenticatesLikeDesktop() throws Exception {
        String dirWithUserInfo = "http://" + USER + ":" + PASS + "@127.0.0.1:" + port + "/upm";
        File downloaded = transport().getRemoteFile(dirWithUserInfo, "upm.db", null, null);
        assertNotNull(downloaded);
        assertArrayEquals(goldenV4, Files.readAllBytes(downloaded.toPath()));
        assertEquals(EXPECTED_AUTH, lastAuthHeader);
    }

    // The sync upload half (deletefile.php + upload.php) authenticates the same way.
    @Test
    public void syncUploadPathsSendTheSameAuthentication() throws Exception {
        String dirWithUserInfo = "http://" + USER + ":" + PASS + "@127.0.0.1:" + port + "/upm";
        HTTPTransport t = transport();
        t.delete(dirWithUserInfo, "upm.db", null, null);
        assertEquals(EXPECTED_AUTH, lastAuthHeader);

        Path dbCopy = Files.createTempFile(tmpDir.toPath(), "upm", ".db");
        Files.write(dbCopy, goldenV4);
        t.put(dirWithUserInfo, dbCopy.toFile(), null, null);
        assertEquals(EXPECTED_AUTH, lastAuthHeader);
    }
}
