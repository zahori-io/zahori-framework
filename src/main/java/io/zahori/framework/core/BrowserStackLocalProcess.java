package io.zahori.framework.core;

import com.browserstack.local.Local;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Binario BrowserStack Local real, a traves de browserstack-local-java. */
final class BrowserStackLocalProcess implements LocalTunnelProcess {

    private static final Logger LOG = LogManager.getLogger(BrowserStackLocalProcess.class);
    // Local.isRunning() en Linux y macOS siempre dice que si: la vida se mira por el pid, que es de paquete
    private static final Field LOCAL_PID = pidField(Local.class, "pid");
    // La libreria descarga y valida el binario sin exclusion: una sola preparacion a la vez en la JVM
    private static final ReentrantLock BINARY_LOCK = new ReentrantLock();

    private final TrackedLocal local;
    private final Supplier<TrackedLocal> localFactory;
    private final LongFunction<Optional<ProcessHandle>> processes;
    private final Field pidField;
    private final LocalTunnelLedger ledger;
    private final Path defaultBinaryLock;
    private final Queue<TrackedLocal> helpers = new ConcurrentLinkedQueue<>();
    private volatile ProcessHandle daemon;
    private volatile Map<String, String> startedArgs;

    BrowserStackLocalProcess(LocalTunnelLedger ledger) {
        this(new TrackedLocal(), TrackedLocal::new, ProcessHandle::of, LOCAL_PID, ledger,
                Path.of(System.getProperty("user.home"), ".browserstack", "BrowserStackLocal.lock"));
    }

    BrowserStackLocalProcess(TrackedLocal local, Supplier<TrackedLocal> localFactory,
            LongFunction<Optional<ProcessHandle>> processes, Field pidField, LocalTunnelLedger ledger, Path defaultBinaryLock) {
        this.local = local;
        this.localFactory = localFactory;
        this.processes = processes;
        this.pidField = pidField;
        this.ledger = ledger;
        this.defaultBinaryLock = defaultBinaryLock;
    }

    @Override
    public void start(Map<String, String> args) throws Exception {
        startedArgs = args;
        prepareBinary(args);
        local.start(args);
        // El handle guarda el instante de arranque: si el pid se reutiliza, deja de estar vivo
        daemon = processes.apply(pid()).orElse(null);
        if (daemon != null) {
            ledger.record(identifierOf(args), daemon);
        }
    }

    @Override
    public boolean isAlive() {
        if (pidField == null) {
            return isRunningByLibrary();
        }
        ProcessHandle handle = daemon;
        return handle != null && handle.isAlive();
    }

    @Override
    public long pid() {
        return pidField == null ? 0 : readPid(pidField, local);
    }

    @Override
    public void stop() throws Exception {
        local.stop();
        Map<String, String> started = startedArgs;
        if (started != null) {
            ledger.forget(identifierOf(started));
        }
    }

    // Instancia nueva: la del intento puede seguir dentro de su start() en otro hilo
    @Override
    public void stopByIdentifier(Map<String, String> args) throws Exception {
        helper().stop(args);
        ledger.forget(identifierOf(args));
    }

    @Override
    public void abort() {
        local.abort();
        helpers.forEach(TrackedLocal::abort);
        // Parada colgada de un tunel que ya daba servicio: se mata el daemon con su fork
        ProcessHandle connected = daemon;
        if (connected != null) {
            ProcessTrees.destroyForcibly(connected);
        }
    }

    /** Descarga y valida el binario sin lanzarlo, de una en una en la JVM y entre JVM de la maquina. */
    private void prepareBinary(Map<String, String> args) throws Exception {
        Map<String, String> onlyCommand = new HashMap<>(args);
        onlyCommand.put("onlyCommand", "true");
        Path lockFile = binaryLockOf(args);
        BINARY_LOCK.lockInterruptibly();
        try {
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                helper().start(onlyCommand);
            }
        } finally {
            BINARY_LOCK.unlock();
        }
    }

    Path binaryLockOf(Map<String, String> args) {
        String binaryPath = args.get("binarypath");
        return StringUtils.isBlank(binaryPath) ? defaultBinaryLock : Path.of(binaryPath.trim() + ".lock").toAbsolutePath();
    }

    private TrackedLocal helper() {
        TrackedLocal fresh = localFactory.get();
        helpers.add(fresh);
        return fresh;
    }

    private static String identifierOf(Map<String, String> args) {
        return args.get(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG);
    }

    private boolean isRunningByLibrary() {
        try {
            return local.isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    static long readPid(Field field, Local target) {
        try {
            return field.getInt(target);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    /** @return el campo, o null si otra version de la libreria no lo tiene */
    static Field pidField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("BrowserStack Local pid not readable ({}): falling back to Local.isRunning()", e.toString());
            return null;
        }
    }

    /** Local que deja matar lo que lanza y, abortado, no lanza nada mas. */
    static class TrackedLocal extends Local {
        private volatile Process launched;
        private volatile boolean aborted;

        @Override
        protected LocalProcess runCommand(List<String> command) throws IOException {
            if (aborted) {
                throw new IOException("BrowserStack Local aborted");
            }
            Process process = launch(command);
            launched = process;
            // El aborto pudo llegar mientras se lanzaba
            if (aborted) {
                ProcessTrees.destroyForcibly(process.toHandle());
                throw new IOException("BrowserStack Local aborted");
            }
            return new LocalProcess() {
                @Override
                public InputStream getInputStream() {
                    return process.getInputStream();
                }

                @Override
                public InputStream getErrorStream() {
                    return process.getErrorStream();
                }

                @Override
                public int waitFor() throws Exception {
                    return process.waitFor();
                }
            };
        }

        Process launch(List<String> command) throws IOException {
            return new ProcessBuilder(command).start();
        }

        void abort() {
            aborted = true;
            Process process = launched;
            if (process != null) {
                ProcessTrees.destroyForcibly(process.toHandle());
            }
        }
    }
}
