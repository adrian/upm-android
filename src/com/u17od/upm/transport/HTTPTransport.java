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
package com.u17od.upm.transport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.u17od.upm.util.Base64;
import com.u17od.upm.util.Util;


public class HTTPTransport extends Transport {

    private static final String BOUNDRY = "==================================";

    private File certFile;
    private SSLSocketFactory sslFactory;
    private String trustedHost;
    private File tmpDir;


    public HTTPTransport(File certFile, String trustedHost, File tmpDir) {
        this.certFile = certFile;
        this.trustedHost = trustedHost;
        this.tmpDir = tmpDir;
    }

    public void put(String targetLocation, File file) throws TransportException {
        put(targetLocation, file, null, null);
    }    
    
    public void put(String targetLocation, File file, String username, String password) throws TransportException {

        HttpURLConnection conn = null; 

        try {
            targetLocation = addTrailingSlash(targetLocation) + "upload.php";

            // These strings are sent in the request body. They provide information about the file being uploaded
            String contentDisposition = "Content-Disposition: form-data; name=\"userfile\"; filename=\"" + file.getName() + "\"";
            String contentType = "Content-Type: application/octet-stream";

            // This is the standard format for a multipart request
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write("--".getBytes());
            baos.write(BOUNDRY.getBytes());
            baos.write("\n".getBytes());
            baos.write(contentDisposition.getBytes());
            baos.write("\n".getBytes());
            baos.write(contentType.getBytes());
            baos.write("\n".getBytes());
            baos.write("\n".getBytes());
            baos.write(Util.getBytesFromFile(file));
            baos.write("\n".getBytes());
            baos.write("--".getBytes());
            baos.write(BOUNDRY.getBytes());
            baos.write("--".getBytes());

            // Make a connect to the server
            URL url = new URL(targetLocation);
            conn = getConnection(url);

            // Put the authentication details in the request
            if (username != null) {
                conn.setRequestProperty ("Authorization", createAuthenticationString(username, password));
            }

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDRY);

            // Send the body
            DataOutputStream dataOS = new DataOutputStream(conn.getOutputStream());
            baos.writeTo(dataOS);
            dataOS.flush();
            dataOS.close();

            // Ensure we got the HTTP 200 response code
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new TransportException(String.format("Received the response code %d from the URL %s", responseCode, url));
            }

            // Read the response
            InputStream is = conn.getInputStream();
            byte[] bytesReceived = readFromResponseStream(is);
            is.close();
            String response = new String(bytesReceived);
            
            if (!response.toString().equals("OK")) {
                throw new TransportException(String.format("Received the response code %s from the URL %s", response, url));
            }

        } catch (Exception e) {
            throw new TransportException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

    }


    public byte[] get(String urlToGet) throws TransportException {
        return get(urlToGet, null, null);
    }
    
    
    public byte[] get(String urlToGet, String username, String password) throws TransportException {

        byte[] bytesReceived = null;

        HttpURLConnection conn = null; 

        try {
            // Make a connect to the server
            URL url = new URL(urlToGet);
            conn = getConnection(url);

            // Put the authentication details in the request
            if (username != null && !username.trim().equals("")) {
                conn.setRequestProperty ("Authorization", createAuthenticationString(username, password));
            }

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");

            // Ensure we get the either 200 or 404
            // 200 is OK
            // 404 means file doesn't exist. This is a valid result so we just return null
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read the response
                InputStream is = conn.getInputStream();
                bytesReceived = readFromResponseStream(is);
                is.close();
                conn.getInputStream().close();
            } else if (responseCode != 404) {
                throw new TransportException(String.format("Received the response code %d from the URL %s", responseCode, url));
            }

        } catch (MalformedURLException e) {
            throw new TransportException(e);
        } catch (IOException e) {
            throw new TransportException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return bytesReceived;

    }

    
    private HttpURLConnection getConnection(URL url) throws TransportException {
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
            
            // This is for testing purposes. Setting http.proxyHost and http.proxyPort
            // doesn't seem to work but this does.
//            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8888));
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);

            if(conn instanceof HttpsURLConnection) {
                if (certFile.exists() && sslFactory == null) {
                    buildSSLFactory();
                }
                if (sslFactory != null) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                    // If we've been provided with a hostname we should always
                    // trust then add a HostnameVerifier for that hostname
                    if (trustedHost != null) {
                        ((HttpsURLConnection) conn).setHostnameVerifier(
                                new HostnameVerifier() {
                                    @Override
                                    public boolean verify(String hostname, SSLSession session) {
                                        return hostname.equals(trustedHost);
                                    }
                                }
                        );
                    }
                }
            }
        } catch (IOException e) {
            throw new TransportException(e);
        } catch (KeyManagementException e) {
            throw new TransportException(e);
        } catch (CertificateException e) {
            throw new TransportException(e);
        } catch (KeyStoreException e) {
            throw new TransportException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new TransportException(e);
        }

        return conn;
    }

    private void buildSSLFactory() throws CertificateException, KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
        FileInputStream fileStream = new FileInputStream(certFile);

        CertificateFactory certFactory  = CertificateFactory.getInstance("X.509");
        Certificate cert = certFactory.generateCertificate(fileStream);
            
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        keyStore.setCertificateEntry("cert0", cert);
            
        TrustManagerFactory trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManager.init(keyStore);
            
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManager.getTrustManagers(), null);

        sslFactory = context.getSocketFactory();
    }

    private byte[] readFromResponseStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int bytesRead;
        while((bytesRead = is.read(bytes)) != -1) {
            baos.write(bytes, 0, bytesRead);
        }
        byte[] bytesToReturn = baos.toByteArray();
        baos.close();
        return bytesToReturn;
    }


    public File getRemoteFile(String remoteLocation) throws TransportException {
        return getRemoteFile(remoteLocation, null, null);
    }


    public File getRemoteFile(String remoteLocation, String fileName, String httpUsername, String httpPassword) throws TransportException {
        remoteLocation = addTrailingSlash(remoteLocation);
        return getRemoteFile(remoteLocation + fileName, httpUsername, httpPassword);
    }


    public File getRemoteFile(String remoteLocation, String httpUsername, String httpPassword) throws TransportException {
        try {
            File downloadedFile = null;
            byte[] remoteFile = get(remoteLocation, httpUsername, httpPassword);
            if (remoteFile != null) {
                downloadedFile = File.createTempFile("upm", null, tmpDir);
                FileOutputStream fos = new FileOutputStream(downloadedFile);
                fos.write(remoteFile);
                fos.close();
            }
            return downloadedFile;
        } catch (IOException e) {
            throw new TransportException(e);
        }
    }


    public void delete(String sharedDbURL, String fileToDelete, String username, String password) throws TransportException {

        HttpURLConnection conn = null;
        String targetURL = addTrailingSlash(sharedDbURL) + "deletefile.php";

        String requestBody = String.format("fileToDelete=%s&Delete=Submit+Query", fileToDelete);

        try {
            // Make a connect to the server
            URL url = new URL(targetURL);
            conn = getConnection(url);

            // Put the authentication details in the request
            if (username != null) {
                conn.setRequestProperty ("Authorization", createAuthenticationString(username, password));
            }

            int contentLength = requestBody.length();
            conn.setRequestProperty("Content-Length", String.valueOf(contentLength));
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setFixedLengthStreamingMode(contentLength);

            // Send the body
            OutputStreamWriter dataOS = new OutputStreamWriter(conn.getOutputStream());
            dataOS.write(requestBody);
            dataOS.flush();
            dataOS.close();

            // Ensure we got the HTTP 200 response code
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new TransportException(String.format("Received the response code %d from the URL %s", responseCode, url));
            }

            // Read the response
            InputStream is = conn.getInputStream();
            byte[] bytesReceived = readFromResponseStream(is);
            is.close();
            String response = new String(bytesReceived);

            // If we don't get OK or FILE_DOESNT_EXIST then thrown an error
            if (!response.toString().equals("OK") && !response.toString().equals("FILE_DOESNT_EXIST")) {
                throw new TransportException(String.format("Received the response code %s from the URL %s", response, url));
            }

        } catch (Exception e) {
            throw new TransportException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

    }


    public void delete(String targetLocation, String fileToDelete) throws TransportException {
        delete(targetLocation, fileToDelete, null, null);
    }


    private String addTrailingSlash(String url) {
        if (url.charAt(url.length() - 1) != '/') {
            url = url + '/';
        }
        return url;
    }


    private String createAuthenticationString(String username, String password) {
        String usernamePassword = username + ":" + password;
        String encodedUsernamePassword = Base64.encodeBytes(usernamePassword.getBytes());
        return "Basic " + encodedUsernamePassword;
    }

}
