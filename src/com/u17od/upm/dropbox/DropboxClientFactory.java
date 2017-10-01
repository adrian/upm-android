package com.u17od.upm.dropbox;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.v2.DbxClientV2;

/**
 * Singleton instance of {@link DbxClientV2} and friends
 */
public class DropboxClientFactory {

    private static DbxClientV2 sDbxClient;
    private static String accessToken;

    public static void init(String accessToken) {
        if (sDbxClient == null || DropboxClientFactory.accessToken != accessToken) {
            DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("upm")
                    .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                    .build();

            sDbxClient = new DbxClientV2(requestConfig, accessToken);
            DropboxClientFactory.accessToken = accessToken;
        }
    }

    public static DbxClientV2 getClient() {
        if (sDbxClient == null) {
            throw new IllegalStateException("Client not initialized.");
        }
        return sDbxClient;
    }
}
