package com.dervarex.unweird.install;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallerTest {

    static final Kind MODS = new Kind("mods", "Mods");
    static final Kind PACKS = new Kind("packs", "Packs");

    @TempDir
    Path root;
    @TempDir
    Path scratch;

    HttpServer server;
    final Map<String, byte[]> files = new HashMap<>();
    volatile CountDownLatch gate;          // when set, downloads wait for it
    final Map<String, Function<Item, Release>> releases = new HashMap<>();
    Installer installer;

    final Source source = new Source() {
        @Override
        public List<Kind> kinds() {
            return List.of(MODS, PACKS);
        }

        @Override
        public Results search(String query, Kind kind, int offset) {
            return new Results(List.of(), 0);
        }

        @Override
        public Release release(Item item) {
            Function<Item, Release> release = releases.get(item.id());
            if (release == null) {
                throw new IllegalStateException("No version of " + item.title() + " fits");
            }
            return release.apply(item);
        }
    };

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring(1);
            byte[] body = files.get(name);
            try {
                CountDownLatch wait = gate;
                if (wait != null) {
                    wait.await(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        installer = new Installer(source, root);
    }

    @AfterEach
    void stop() {
        installer.close();
        server.stop(0);
    }

    String url(String name) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + name;
    }

    /** Serves a file and returns its SHA-1. */
    String serve(String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        files.put(name, bytes);
        return sha1(bytes);
    }

    static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void mod(String id, String version, String file, String content, String... requires) {
        String sha1 = serve(file, content);
        releases.put(id, item -> {
            Release.Builder release = Release.version(version).download(url(file), "mods/" + file, sha1, content.length());
            for (String dependency : requires) {
                release.requires(Item.of(dependency, MODS));
            }
            return release.build();
        });
    }

    static Item item(String id) {
        return Item.of(id, MODS);
    }

    @Test
    void blankIconUrlsMeanNoIcon() {
        Item item = new Item("x", MODS, "X", null, null, "  ", null);
        assertEquals(null, item.iconUrl());
        new ItemCard(item, installer); // must not throw
    }

    @Test
    void installsWithDependenciesAndRemembersAfterARestart() throws Exception {
        mod("sodium", "1.0", "sodium.jar", "fast", "fabric-api");
        mod("fabric-api", "0.9", "fabric-api.jar", "api");

        installer.install(item("sodium")).get();

        assertEquals(Installer.State.INSTALLED, installer.status("sodium").state());
        assertEquals(Installer.State.INSTALLED, installer.status("fabric-api").state());
        assertEquals("fast", Files.readString(root.resolve("mods/sodium.jar")));
        assertTrue(installer.installed().stream().anyMatch(i -> i.item().id().equals("fabric-api") && i.dependency()));

        try (Installer restarted = new Installer(source, root)) {
            assertTrue(restarted.status("sodium").installed());
        }
    }

    @Test
    void aDamagedDownloadFailsCleanlyAndCanBeRetried() throws Exception {
        serve("broken.jar", "what arrives");
        releases.put("broken", item -> Release.version("1").download(url("broken.jar"), "mods/broken.jar", sha1("what it should be".getBytes()), 12).build());

        var error = assertThrows(ExecutionException.class, () -> installer.install(item("broken")).get());
        assertTrue(error.getCause().getMessage().contains("damaged"), error.getCause().getMessage());
        Installer.Status status = installer.status("broken");
        assertEquals(Installer.State.FAILED, status.state());
        assertEquals("Retry", status.label());
        try (var listing = Files.list(root.resolve("mods"))) {
            assertEquals(0, listing.count(), "nothing may be left behind, not even .part files");
        }

        mod("broken", "2", "broken.jar", "fixed");
        installer.install(item("broken")).get();
        assertTrue(installer.status("broken").installed());
    }

    @Test
    void aFailingDependencyRollsBackTheWholeInstall() throws Exception {
        mod("needs-missing", "1", "a.jar", "a", "missing");
        assertThrows(ExecutionException.class, () -> installer.install(item("needs-missing")).get());
        assertEquals(Installer.State.FAILED, installer.status("needs-missing").state());
        assertTrue(installer.status("needs-missing").message().contains("No version of missing"));
        assertFalse(Files.exists(root.resolve("mods/a.jar")));
    }

    @Test
    void deletingAFileByHandMeansNotInstalled() throws Exception {
        mod("lithium", "1", "lithium.jar", "lithium");
        installer.install(item("lithium")).get();
        Files.delete(root.resolve("mods/lithium.jar"));
        assertEquals(Installer.State.NOT_INSTALLED, installer.status("lithium").state());
        assertEquals("Install", installer.status("lithium").label());
    }

    @Test
    void uninstallRemovesDependenciesNothingElseNeeds() throws Exception {
        mod("a", "1", "a.jar", "a", "lib");
        mod("b", "1", "b.jar", "b", "lib");
        mod("lib", "1", "lib.jar", "lib");
        installer.install(item("a")).get();
        installer.install(item("b")).get();

        installer.uninstall("a").get();
        assertTrue(installer.status("lib").installed(), "b still needs it");
        assertFalse(Files.exists(root.resolve("mods/a.jar")));

        installer.uninstall("b").get();
        assertFalse(installer.status("lib").installed());
        assertFalse(Files.exists(root.resolve("mods/lib.jar")));
    }

    @Test
    void explicitlyInstalledDependenciesStay() throws Exception {
        mod("a", "1", "a.jar", "a", "lib");
        mod("lib", "1", "lib.jar", "lib");
        installer.install(item("lib")).get();
        installer.install(item("a")).get();
        installer.uninstall("a").get();
        assertTrue(installer.status("lib").installed());
    }

    @Test
    void updatingRemovesTheOldFile() throws Exception {
        mod("iris", "1", "iris-1.jar", "v1");
        installer.install(item("iris")).get();
        mod("iris", "2", "iris-2.jar", "v2");
        installer.install(item("iris")).get();
        assertFalse(Files.exists(root.resolve("mods/iris-1.jar")));
        assertEquals("v2", Files.readString(root.resolve("mods/iris-2.jar")));
        assertEquals("2", installer.installed().getFirst().version());
    }

    @Test
    void statusShowsInstallingWhileDownloading() throws Exception {
        mod("slow", "1", "slow.jar", "slow");
        gate = new CountDownLatch(1);
        var job = installer.install(item("slow"));
        Thread.sleep(200);
        assertTrue(installer.status("slow").busy(), installer.status("slow").toString());
        assertTrue(installer.install(item("slow")) == job, "a second install joins the running one");
        gate.countDown();
        job.get();
        assertTrue(installer.status("slow").installed());
    }

    @Test
    void archivesAreUnpackedAndOverridesDontCountAsMissing() throws Exception {
        Path pack = scratch.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry("overrides/config/options.txt"));
            zip.write("fov=90".getBytes());
            zip.putNextEntry(new ZipEntry("other/ignored.txt"));
            zip.write("x".getBytes());
        }
        String sha1 = serve("pack-mod.jar", "mod in pack");
        releases.put("pack", item -> Release.version("1.0")
                .download(url("pack-mod.jar"), "mods/pack-mod.jar", sha1, 11)
                .unpack(pack, "overrides/", "")
                .info("minecraft", "1.21.1")
                .temporary(pack)
                .build());

        installer.install(Item.of("pack", PACKS)).get();
        assertEquals("fov=90", Files.readString(root.resolve("config/options.txt")));
        assertFalse(Files.exists(root.resolve("ignored.txt")));
        assertFalse(Files.exists(pack), "temporary files are cleaned up");
        assertEquals("1.21.1", installer.installed().getFirst().info().get("minecraft"));

        Files.delete(root.resolve("config/options.txt")); // users may delete configs...
        assertTrue(installer.status("pack").installed()); // ...the pack is still installed

        installer.uninstall("pack").get();
        assertFalse(Files.exists(root.resolve("mods/pack-mod.jar")));
    }

    @Test
    void pathsOutsideTheRootAreRefused() throws Exception {
        String sha1 = serve("evil.jar", "evil");
        releases.put("evil", item -> Release.version("1").download(url("evil.jar"), "../evil.jar", sha1, 4).build());
        assertThrows(ExecutionException.class, () -> installer.install(item("evil")).get());
        assertFalse(Files.exists(root.getParent().resolve("evil.jar")));

        Path zip = scratch.resolve("slip.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("overrides/../../escaped.txt"));
            out.write("x".getBytes());
        }
        releases.put("slip", item -> Release.version("1").unpack(zip, "overrides/", "").build());
        assertThrows(ExecutionException.class, () -> installer.install(item("slip")).get());
        assertFalse(Files.exists(root.getParent().resolve("escaped.txt")));
        assertEquals(Installer.State.FAILED, installer.status("slip").state());

        assertThrows(IllegalArgumentException.class, () -> installer.inside(".unweird-installed.json"));
    }
}
