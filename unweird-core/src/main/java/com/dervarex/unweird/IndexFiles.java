package com.dervarex.unweird;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Reads the index files that unweird-processor writes. Every module on the classpath may contribute one. */
final class IndexFiles {

    private IndexFiles() {
    }

    /** All non-blank lines of every copy of {@code resource} on the classpath. */
    static List<String> lines(String resource) {
        List<String> lines = new ArrayList<>();
        try {
            Enumeration<URL> files = loader().getResources(resource);
            while (files.hasMoreElements()) {
                try (var reader = new BufferedReader(new InputStreamReader(files.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    reader.lines().map(String::strip).filter(line -> !line.isEmpty()).forEach(lines::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("unweird: could not read " + resource, e);
        }
        return lines;
    }

    static ClassLoader loader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : IndexFiles.class.getClassLoader();
    }
}
