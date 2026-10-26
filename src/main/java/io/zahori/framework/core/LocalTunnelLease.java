package io.zahori.framework.core;

import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelLog;
import io.zahori.framework.core.BrowserStackLocalTunnel.TunnelUnavailableException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tunel compartido visto desde un caso. El binario puede seguir vivo con el tunel caido: lo
 * sabe BrowserStack al pedir la sesion, y entonces se releva y se pide otra vez, una sola.
 */
final class LocalTunnelLease {

    private final BrowserStackLocalTunnel tunnel;
    private final Map<String, String> args;
    private final TunnelLog log;
    private LocalTunnelProcess process;

    private LocalTunnelLease(BrowserStackLocalTunnel tunnel, Map<String, String> args, TunnelLog log) {
        this.tunnel = tunnel;
        this.args = args;
        this.log = log;
    }

    static LocalTunnelLease acquire(BrowserStackLocalTunnel tunnel, Map<String, String> args, TunnelLog log)
            throws TunnelUnavailableException {
        LocalTunnelLease lease = new LocalTunnelLease(tunnel, args, log);
        lease.take();
        return lease;
    }

    /** Crea la sesion; si BrowserStack no ve el tunel, releva el que uso este caso y la pide otra vez. */
    <T> T create(Supplier<T> session) throws TunnelUnavailableException {
        try {
            return session.get();
        } catch (RuntimeException e) {
            String reason = BrowserStackLocalFailures.reasonOf(e);
            if (reason == null) {
                throw e;
            }
            tunnel.recycle(process, reason, log);
            take();
            return session.get();
        }
    }

    private void take() throws TunnelUnavailableException {
        tunnel.acquire(args, log);
        process = tunnel.current();
    }
}
