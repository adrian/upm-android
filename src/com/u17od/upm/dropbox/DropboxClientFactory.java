package com.u17od.upm.dropbox;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.v2.DbxClientV2;

/**
 * Singleton instance of {@link DbxClientV2} and friends
 */
public class DropboxClientFactory {

    private static DbxClientV2 client;
    private static String accessToken;

    public static void init(String accessToken) {
        if (client == null || !DropboxClientFactory.accessToken.equals(accessToken)) {
            DbxRequestConfig config = new DbxRequestConfig("upm");
            DropboxClientFactory.client = new DbxClientV2(config, accessToken);
            DropboxClientFactory.accessToken = accessToken;
        }
    }

    public static DbxClientV2 getClient() {
        if (client == null) {
            throw new IllegalStateException("Client not initialized.");
        }
        return client;
    }
}
