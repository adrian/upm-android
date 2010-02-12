/*
 * $Id: HTTPTransport.java 174 2006-12-21 16:00:02Z adrianshome $
 * 
 * Universal Password Manager
 * Copyright (c) 2010 Adrian Smith
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

import com.u17od.upm.util.Base64;
import com.u17od.upm.util.Util;


public class HTTPTransport extends Transport {

    private static final String BOUNDRY = "==================================";

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
            StringBuffer requestBody = new StringBuffer();
            requestBody.append("--");
            requestBody.append(BOUNDRY);
            requestBody.append('\n');
            requestBody.append(contentDisposition);
            requestBody.append('\n');
            requestBody.append(contentType);
            requestBody.append('\n');
            requestBody.append('\n');
            requestBody.append(new String(Util.getBytesFromFile(file)));
            requestBody.append("--");
            requestBody.append(BOUNDRY);
            requestBody.append("--");

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
            dataOS.writeBytes(requestBody.toString());
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
            if (username != null) {
                conn.setRequestProperty ("Authorization", createAuthenticationString(username, password));
            }

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");

            // Ensure we got the HTTP 200 response code
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new TransportException(String.format("Received the response code %d from the URL %s", responseCode, url));
            }

            // Read the response
            InputStream is = conn.getInputStream();
            bytesReceived = readFromResponseStream(is);
            is.close();
            conn.getInputStream().close();

        } catch (Exception e) {
            throw new TransportException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return bytesReceived;

    }

    
    private HttpURLConnection getConnection(URL url) throws IOException {
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // This is for testing purposes. Setting http.proxyHost and http.proxyPort
        // doesn't seem to work but this does.
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8888));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);

        return conn;
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

    
    public File getRemoteFile(String remoteLocation, String httpUsername, String httpPassword) throws TransportException {
        try {
            byte[] remoteFile = get(remoteLocation, httpUsername, httpPassword);
            File downloadedFile = File.createTempFile("upm", null);
            FileOutputStream fos = new FileOutputStream(downloadedFile);
            fos.write(remoteFile);
            fos.close();
            return downloadedFile;
        } catch (IOException e) {
            throw new TransportException(e);
        }
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
