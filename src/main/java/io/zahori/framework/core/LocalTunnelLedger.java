package io.zahori.framework.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Daemons de cada JVM apuntados por pid y arranque: recoge los de una JVM muerta sin leer la linea de comandos. */
final class LocalTunnelLedger {

    private static final Logger LOG = LogManager.getLogger(LocalTunnelLedger.class);
    private static final String SUFFIX = ".tunnel";
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final Path directory;
    private final ProcessHandle jvm;
    private final LongFunction<Optional<ProcessHandle>> processes;
    private final Predicate<ProcessHandle> terminator;

    LocalTunnelLedger(Path directory, ProcessHandle jvm, LongFunction<Optional<ProcessHandle>> processes) {
        this(directory, jvm, processes, terminator(GRACE));
    }

    LocalTunnelLedger(Path directory, ProcessHandle jvm, LongFunction<Optional<ProcessHandle>> processes,
            Predicate<ProcessHandle> terminator) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.jvm = Objects.requireNonNull(jvm, "jvm");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.terminator = Objects.requireNonNull(terminator, "terminator");
    }

    static LocalTunnelLedger forThisJvm() {
        return new LocalTunnelLedger(Path.of(System.getProperty("java.io.tmpdir"), "zahori-browserstack-local"),
                ProcessHandle.current(), ProcessHandle::of);
    }

    /** Apunta el daemon conectado del tunel; si no se puede, el tunel sigue sin red de seguridad. */
    void record(String identifier, ProcessHandle daemon) {
        Properties entry = new Properties();
        entry.setProperty("daemonPid", String.valueOf(daemon.pid()));
        entry.setProperty("daemonStart", startOf(daemon));
        entry.setProperty("jvmPid", String.valueOf(jvm.pid()));
        entry.setProperty("jvmStart", startOf(jvm));
        try {
            Files.createDirectories(directory);
            try (OutputStream out = Files.newOutputStream(fileOf(identifier))) {
                entry.store(out, null);
            }
        } catch (IOException e) {
            LOG.warn("BrowserStack Local daemon {} not recorded: {}", identifier, e.toString());
        }
    }

    void forget(String identifier) {
        try {
            Files.deleteIfExists(fileOf(identifier));
        } catch (IOException e) {
            LOG.warn("BrowserStack Local daemon {} not forgotten: {}", identifier, e.toString());
        }
    }

    /** Para los daemons cuya JVM ya no existe (kill -9, OOM, cierre de consola) y devuelve cuantos. */
    int reapOrphans() {
        int reaped = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path file : entries) {
                reaped += reapQuietly(file);
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("BrowserStack Local orphans not reaped: {}", e.toString());
        }
        return reaped;
    }

    // Un fichero corrupto o ajeno no impide recoger los demas
    private int reapQuietly(Path file) {
        try {
            return reap(file);
        } catch (IOException | RuntimeException e) {
            LOG.warn("BrowserStack Local orphan {} not reaped: {}", file.getFileName(), e.toString());
            return 0;
        }
    }

    private int reap(Path file) throws IOException {
        Properties entry = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            entry.load(in);
        }
        if (isAlive(entry.getProperty("jvmPid"), entry.getProperty("jvmStart"))) {
            return 0;
        }
        boolean stopped = running(entry.getProperty("daemonPid"), entry.getProperty("daemonStart"))
                .map(terminator::test)
                .orElse(false);
        Files.deleteIfExists(file);
        return stopped ? 1 : 0;
    }

    static Predicate<ProcessHandle> terminator(Duration grace) {
        return daemon -> terminate(daemon, grace);
    }

    /** Parada normal para que el daemon se desconecte de BrowserStack; si no muere a tiempo, forzada. */
    static boolean terminate(ProcessHandle daemon, Duration grace) {
        List<ProcessHandle> forks = ProcessTrees.descendantsOf(daemon);
        daemon.destroy();
        if (!exited(daemon, grace)) {
            daemon.destroyForcibly();
        }
        boolean stopped = exited(daemon, grace);
        // En Windows el fork no muere con el daemon
        forks.forEach(ProcessHandle::destroyForcibly);
        return stopped && forks.stream().allMatch(fork -> exited(fork, grace));
    }

    private static boolean exited(ProcessHandle process, Duration grace) {
        try {
            process.onExit().get(grace.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        } catch (ExecutionException | TimeoutException e) {
            return !process.isAlive();
        }
    }

    private boolean isAlive(String pid, String start) {
        return running(pid, start).isPresent();
    }

    // El instante de arranque distingue el proceso apuntado de otro que reutilice su pid
    private Optional<ProcessHandle> running(String pid, String start) {
        try {
            return processes.apply(Long.parseLong(pid))
                    .filter(ProcessHandle::isAlive)
                    .filter(handle -> start.isEmpty() || start.equals(startOf(handle)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String startOf(ProcessHandle handle) {
        return handle.info().startInstant().map(Instant::toString).orElse("");
    }

    private Path fileOf(String identifier) {
        return directory.resolve(identifier + SUFFIX);
    }
}
