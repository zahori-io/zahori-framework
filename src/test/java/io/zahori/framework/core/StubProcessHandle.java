package io.zahori.framework.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Proceso que muere, o no, segun como se le pida, igual en Windows, Linux y macOS. Sus hijos no
 * mueren con el y, muerto, ya no los lista: como el fork de BrowserStackLocal en Windows.
 */
final class StubProcessHandle implements ProcessHandle {
    final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();
    final List<ProcessHandle> forks = new ArrayList<>();
    private final boolean diesWhenAsked;
    private final boolean diesWhenForced;
    int destroys;
    int forced;

    StubProcessHandle(boolean diesWhenAsked, boolean diesWhenForced) {
        this.diesWhenAsked = diesWhenAsked;
        this.diesWhenForced = diesWhenForced;
    }

    @Override
    public long pid() {
        return 4242;
    }

    @Override
    public Optional<ProcessHandle> parent() {
        return Optional.empty();
    }

    @Override
    public Stream<ProcessHandle> children() {
        return descendants();
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        return isAlive() ? forks.stream() : Stream.empty();
    }

    @Override
    public Info info() {
        return ProcessHandle.current().info();
    }

    @Override
    public CompletableFuture<ProcessHandle> onExit() {
        return exit;
    }

    @Override
    public boolean supportsNormalTermination() {
        return true;
    }

    @Override
    public boolean destroy() {
        destroys++;
        if (diesWhenAsked) {
            exit.complete(this);
        }
        return true;
    }

    @Override
    public boolean destroyForcibly() {
        forced++;
        if (diesWhenForced) {
            exit.complete(this);
        }
        return true;
    }

    @Override
    public boolean isAlive() {
        return !exit.isDone();
    }

    @Override
    public int compareTo(ProcessHandle other) {
        return Long.compare(pid(), other.pid());
    }
}
