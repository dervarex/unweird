package com.dervarex.unweird.install;

import com.dervarex.unweird.Unweird;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs {@link Item}s from a {@link Source} into a root directory and tells you, at any moment, what's installed.
 *
 * <p><b>Installed state is never cached in the UI.</b> {@link #status} is computed from three things:
 * a running job, the last failure, and the manifest ({@code .unweird-installed.json} in the root) checked
 * against the files on disk. So a button like {@code new Button(() -> installer.status(item).label())} is
 * always right, even after the app restarts or someone deletes a file by hand.
 *
 * <p>Installing downloads everything first (verified by SHA-1, via {@code .part} files), then records it in the
 * manifest in one step. If anything fails, what was written is removed again: there is no half-installed state.
 *
 * <p>All methods are thread-safe. Work runs on background threads; every change calls {@link Unweird#refresh()},
 * so the UI updates by itself.
 */
public final class Installer implements AutoCloseable {

    public enum State {
        NOT_INSTALLED,
        /** Asking the source what to install (versions, dependencies). */
        PREPARING,
        INSTALLING,
        REMOVING,
        INSTALLED,
        FAILED;

        /** Something is happening right now. */
        public boolean busy() {
            return this == PREPARING || this == INSTALLING || this == REMOVING;
        }
    }

    /**
     * @param progress 0..1 while installing, otherwise 0
     * @param message  why it failed (for {@link State#FAILED}), else null
     */
    public record Status(State state, double progress, String message) {

        public boolean installed() {
            return state == State.INSTALLED;
        }

        public boolean busy() {
            return state.busy();
        }

        /** A ready-made button label: "Install", "Installing 42%", "Remove", "Retry", ... */
        public String label() {
            return switch (state) {
                case NOT_INSTALLED -> "Install";
                case PREPARING -> "Preparing…";
                case INSTALLING -> "Installing " + Math.round(progress * 100) + "%";
                case REMOVING -> "Removing…";
                case INSTALLED -> "Remove";
                case FAILED -> "Retry";
            };
        }
    }

    /** An installed item. {@code dependency} = only there because something else needs it. */
    public record Installed(Item item, String version, boolean dependency, Map<String, String> info) {
    }

    private static final Status NOT_INSTALLED = new Status(State.NOT_INSTALLED, 0, null);
    private static final Status INSTALLED = new Status(State.INSTALLED, 0, null);

    private final Source source;
    private final Path root;
    private final Manifest manifest;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Installer(Source source, Path root) {
        this.source = Objects.requireNonNull(source, "source");
        this.root = root.toAbsolutePath().normalize();
        this.manifest = Manifest.load(this.root);
    }

    public Source source() {
        return source;
    }

    public Path root() {
        return root;
    }

    /** Runs {@code listener} after every change (besides refreshing the UI). Called on background threads. */
    public void onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ---- state ----

    public Status status(Item item) {
        return status(item.id());
    }

    /** The current state of an item. Cheap; meant to be called from UI lambdas after every event. */
    public Status status(String id) {
        Job job = jobs.get(id);
        if (job != null) {
            return new Status(job.state, job.progress(), null);
        }
        String failure = failures.get(id);
        if (failure != null) {
            return new Status(State.FAILED, 0, failure);
        }
        Manifest.Entry entry = manifest.get(id);
        return entry != null && manifest.present(entry) ? INSTALLED : NOT_INSTALLED;
    }

    /** Everything installed right now (entries whose files are all still on disk). */
    public List<Installed> installed() {
        List<Installed> out = new ArrayList<>();
        for (Manifest.Entry entry : manifest.all()) {
            if (manifest.present(entry)) {
                out.add(new Installed(entry.item(), entry.version(), !entry.explicit(), entry.info()));
            }
        }
        return out;
    }

    // ---- install ----

    /** Installs the item (and what it requires). If it's already being installed, returns that job. */
    public CompletableFuture<Void> install(Item item) {
        Objects.requireNonNull(item, "item");
        Job job = new Job(State.PREPARING);
        Job running = jobs.putIfAbsent(item.id(), job);
        if (running != null) {
            return running.future;
        }
        failures.remove(item.id());
        changed();
        executor.execute(() -> {
            try {
                doInstall(item, job);
                jobs.remove(item.id());
                changed();
                job.future.complete(null);
            } catch (Throwable e) {
                failures.put(item.id(), message(e));
                jobs.remove(item.id());
                changed();
                job.future.completeExceptionally(e);
            }
        });
        return job.future;
    }

    private void doInstall(Item main, Job job) throws IOException {
        // 1. Work out everything that has to be installed.
        Map<String, Planned> plan = new LinkedHashMap<>();
        Set<String> alreadyThere = new LinkedHashSet<>();
        resolve(main, plan, alreadyThere);

        job.total.set(plan.values().stream()
                .flatMap(p -> p.release.downloads().stream())
                .mapToLong(d -> Math.max(0, d.size()))
                .sum());
        job.state = State.INSTALLING;
        changed();

        // 2. Download and unpack. Nothing is recorded yet; on failure, written files are removed again.
        List<Path> written = new ArrayList<>();
        Map<String, List<Manifest.FileRef>> filesById = new LinkedHashMap<>();
        try {
            for (Planned planned : plan.values()) {
                List<Manifest.FileRef> files = new ArrayList<>();
                for (Release.Download download : planned.release.downloads()) {
                    files.add(fetch(download, job, written));
                }
                for (Release.Unpack unpack : planned.release.unpack()) {
                    files.addAll(unpack(unpack, written));
                }
                if (files.isEmpty()) {
                    throw new IOException(planned.item.title() + " has nothing to install");
                }
                filesById.put(planned.item.id(), files);
            }
        } catch (IOException | RuntimeException e) {
            for (Path path : written.reversed()) {
                Files.deleteIfExists(path);
            }
            throw e;
        } finally {
            for (Planned planned : plan.values()) {
                for (Path temporary : planned.release.temporary()) {
                    Files.deleteIfExists(temporary);
                }
            }
        }

        // 3. Record it all at once.
        synchronized (manifest) {
            for (Planned planned : plan.values()) {
                String id = planned.item.id();
                Manifest.Entry previous = manifest.get(id);
                boolean explicit = id.equals(main.id()) || previous != null && previous.explicit();
                Set<String> requiredBy = new LinkedHashSet<>(previous == null ? Set.of() : previous.requiredBy());
                if (!id.equals(main.id())) {
                    requiredBy.add(main.id());
                }
                List<Manifest.FileRef> files = filesById.get(id);
                if (previous != null) {
                    removeStaleFiles(previous, files);
                }
                manifest.put(new Manifest.Entry(id, planned.item.title(), planned.item.kind().id(), planned.item.kind().label(),
                        planned.item.iconUrl(), planned.release.version(), files, explicit, requiredBy, planned.release.info()));
            }
            for (String id : alreadyThere) {
                Manifest.Entry entry = manifest.get(id);
                if (entry != null) {
                    Set<String> requiredBy = new LinkedHashSet<>(entry.requiredBy());
                    requiredBy.add(main.id());
                    manifest.put(entry.withRequiredBy(requiredBy));
                }
            }
            manifest.save();
        }
    }

    private void resolve(Item item, Map<String, Planned> plan, Set<String> alreadyThere) {
        if (plan.containsKey(item.id())) {
            return;
        }
        Release release = Objects.requireNonNull(source.release(item), "the source returned no release for " + item.title());
        plan.put(item.id(), new Planned(item, release));
        for (Item dependency : release.requires()) {
            if (plan.containsKey(dependency.id())) {
                continue;
            }
            if (status(dependency.id()).installed()) {
                alreadyThere.add(dependency.id());
            } else {
                resolve(dependency, plan, alreadyThere);
            }
        }
    }

    private Manifest.FileRef fetch(Release.Download download, Job job, List<Path> written) throws IOException {
        Path target = inside(download.path());
        Files.createDirectories(target.getParent());
        if (download.sha1() != null && Files.isRegularFile(target) && Downloads.sha1(target).equalsIgnoreCase(download.sha1())) {
            job.add(Math.max(0, download.size())); // already there, identical
            return new Manifest.FileRef(manifest.relativize(target), download.sha1(), false);
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            Downloads.to(download.url(), part, download.sha1(), source.userAgent(), bytes -> {
                job.add(bytes);
                maybeChanged(job);
            });
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        boolean existed = Files.exists(target);
        move(part, target);
        if (!existed) {
            written.add(target);
        }
        return new Manifest.FileRef(manifest.relativize(target), download.sha1(), false);
    }

    private List<Manifest.FileRef> unpack(Release.Unpack unpack, List<Path> written) throws IOException {
        List<Manifest.FileRef> files = new ArrayList<>();
        String from = unpack.from().isEmpty() || unpack.from().endsWith("/") ? unpack.from() : unpack.from() + "/";
        try (ZipFile zip = new ZipFile(unpack.archive().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(from)) {
                    continue;
                }
                String relative = entry.getName().substring(from.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path target = inside(unpack.to().isEmpty() ? relative : unpack.to() + "/" + relative);
                Files.createDirectories(target.getParent());
                boolean existed = Files.exists(target);
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!existed) {
                    written.add(target);
                }
                files.add(new Manifest.FileRef(manifest.relativize(target), null, true));
            }
        }
        return files;
    }

    // ---- uninstall ----

    /** Removes the item's files, and dependencies nothing else needs any more. */
    public CompletableFuture<Void> uninstall(Item item) {
        return uninstall(item.id());
    }

    public CompletableFuture<Void> uninstall(String id) {
        Job job = new Job(State.REMOVING);
        Job running = jobs.putIfAbsent(id, job);
        if (running != null) {
            return running.future;
        }
        failures.remove(id);
        changed();
        executor.execute(() -> {
            try {
                synchronized (manifest) {
                    remove(id);
                    manifest.save();
                }
                jobs.remove(id);
                changed();
                job.future.complete(null);
            } catch (Throwable e) {
                failures.put(id, "Couldn't remove: " + message(e));
                jobs.remove(id);
                changed();
                job.future.completeExceptionally(e);
            }
        });
        return job.future;
    }

    /** Caller holds the manifest lock. */
    private void remove(String id) throws IOException {
        Manifest.Entry entry = manifest.remove(id);
        if (entry == null) {
            return;
        }
        Set<String> keep = manifest.pathsOwnedByOthers(id);
        for (Manifest.FileRef ref : entry.files()) {
            if (!keep.contains(ref.path())) {
                Files.deleteIfExists(manifest.resolve(ref.path()));
            }
        }
        // dependencies that were only there for this item go too
        for (Manifest.Entry other : manifest.all()) {
            if (other.requiredBy().contains(id)) {
                Set<String> requiredBy = new HashSet<>(other.requiredBy());
                requiredBy.remove(id);
                if (!other.explicit() && requiredBy.isEmpty()) {
                    remove(other.id());
                } else {
                    manifest.put(other.withRequiredBy(requiredBy));
                }
            }
        }
    }

    /** After a reinstall/update: delete files of the old version that the new one doesn't have. */
    private void removeStaleFiles(Manifest.Entry previous, List<Manifest.FileRef> current) throws IOException {
        Set<String> now = new HashSet<>();
        current.forEach(ref -> now.add(ref.path()));
        Set<String> keep = manifest.pathsOwnedByOthers(previous.id());
        for (Manifest.FileRef ref : previous.files()) {
            if (!now.contains(ref.path()) && !keep.contains(ref.path())) {
                Files.deleteIfExists(manifest.resolve(ref.path()));
            }
        }
    }

    // ---- helpers ----

    /** Resolves a relative path inside the root, refusing anything that would escape it (or overwrite the manifest). */
    Path inside(String relative) {
        if (relative == null || relative.isBlank() || relative.contains("\\") || relative.contains("\0")) {
            throw new IllegalArgumentException("Refusing unsafe path: " + relative);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root) || target.getFileName().toString().startsWith(Manifest.FILE)) {
            throw new IllegalArgumentException("Refusing path outside the install folder: " + relative);
        }
        return target;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        Unweird.refresh();
    }

    /** Progress updates at most ~10 times a second. */
    private void maybeChanged(Job job) {
        long now = System.nanoTime();
        if (now - job.lastNotified > 100_000_000L) {
            job.lastNotified = now;
            changed();
        }
    }

    private static String message(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException || cause.getMessage() == null) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /** Stops background work. Running jobs are interrupted. */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record Planned(Item item, Release release) {
    }

    private static final class Job {
        volatile State state;
        final AtomicLong done = new AtomicLong();
        final AtomicLong total = new AtomicLong();
        final CompletableFuture<Void> future = new CompletableFuture<>();
        volatile long lastNotified;

        Job(State state) {
            this.state = state;
        }

        void add(long bytes) {
            done.addAndGet(bytes);
        }

        double progress() {
            long all = total.get();
            return all <= 0 ? 0 : Math.min(1, done.get() / (double) all);
        }
    }
}
