package com.dervarex.unweird.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.LongConsumer;

/** Downloading with hash checks. Used by {@link Installer}; sources can use {@link #temporary} too. */
public final class Downloads {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Downloads() {
    }

    /**
     * Downloads {@code url} into a temporary file and checks its SHA-1 (if given). The caller deletes the file,
     * or hands it to {@link Release.Builder#temporary} so the installer does.
     */
    public static Path temporary(String url, String sha1, String userAgent) throws IOException {
        Path file = Files.createTempFile("unweird-", ".download");
        try {
            to(url, file, sha1, userAgent, bytes -> { });
            return file;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
    }

    /** Downloads into {@code target} (replacing it), reporting bytes as they arrive. Fails on a hash mismatch. */
    static void to(String url, Path target, String sha1, String userAgent, LongConsumer progress) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(10))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("Download failed (HTTP " + response.statusCode() + "): " + url);
        }
        MessageDigest digest = sha1Digest();
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                progress.accept(read);
            }
        }
        if (sha1 != null && !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha1)) {
            Files.deleteIfExists(target);
            throw new IOException("The download of " + target.getFileName() + " was damaged (checksum mismatch)");
        }
    }

    /** SHA-1 of a file, hex. */
    static String sha1(Path file) throws IOException {
        MessageDigest digest = sha1Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
