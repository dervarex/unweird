package com.dervarex.unweird;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** Plain HTTP GET with {@link HttpURLConnection}, which (unlike {@code java.net.http}) also exists on Android. */
final class Http {

    private Http() {
    }

    /** Downloads {@code url}, following redirects. Blocks; call it off the UI thread. */
    static byte[] get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "unweird");
        try {
            int status = connection.getResponseCode();
            if (status / 100 != 2) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
    }
}
