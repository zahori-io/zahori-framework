package io.zahori.framework.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Un tunel por cuenta y opciones; el localIdentifier lo pone cada tunel. */
final class BrowserStackLocalTunnelRegistry {

    private static final Logger LOG = LogManager.getLogger(BrowserStackLocalTunnelRegistry.class);
    private static final int MAX_HOST_LENGTH = 32;

    private final Function<String, BrowserStackLocalTunnel> tunnelFactory;
    private final String identifierPrefix;
    private final AtomicInteger created = new AtomicInteger();
    private final ConcurrentMap<Map<String, String>, BrowserStackLocalTunnel> tunnels = new ConcurrentHashMap<>();

    BrowserStackLocalTunnelRegistry(Function<String, BrowserStackLocalTunnel> tunnelFactory, String identifierPrefix) {
        this.tunnelFactory = Objects.requireNonNull(tunnelFactory, "tunnelFactory");
        this.identifierPrefix = Objects.requireNonNull(identifierPrefix, "identifierPrefix");
    }

    /** Tuneles reales de esta JVM; antes para los daemons que dejo una JVM muerta. */
    static BrowserStackLocalTunnelRegistry forThisProcess() {
        LocalTunnelLedger ledger = LocalTunnelLedger.forThisJvm();
        reapOrphans(ledger);
        ProcessHandle jvm = ProcessHandle.current();
        String prefix = identifierPrefix(localHostName(() -> InetAddress.getLocalHost().getHostName()), jvm.pid(),
                jvm.info().startInstant().orElseGet(Instant::now));
        Supplier<LocalTunnelProcess> binaries = realBinaries(ledger);
        return new BrowserStackLocalTunnelRegistry(identifier -> new BrowserStackLocalTunnel(identifier, binaries), prefix);
    }

    static Supplier<LocalTunnelProcess> realBinaries(LocalTunnelLedger ledger) {
        return () -> new BrowserStackLocalProcess(ledger);
    }

    // reapOrphans no lanza: un fallo aqui dejaria al proceso sin tuneles
    static void reapOrphans(LocalTunnelLedger ledger) {
        LOG.info("BrowserStack Local daemons left by dead Zahori processes on {} {}: {} stopped",
                System.getProperty("os.name"), System.getProperty("os.arch"), ledger.reapOrphans());
    }

    /** Argumentos del binario para una cuenta; binaryPath fija un binario ya instalado en la maquina. */
    static Map<String, String> argsFor(String accessKey, String binaryPath) {
        Map<String, String> args = new HashMap<>();
        args.put("key", accessKey);
        // Todo el trafico del dispositivo sale por esta maquina
        args.put("forcelocal", "true");
        // Solo Automate: sin el puerto de Live no choca con otros daemons de la maquina
        args.put("onlyAutomate", "true");
        if (StringUtils.isNotBlank(binaryPath)) {
            args.put("binarypath", binaryPath.trim());
        }
        return args;
    }

    BrowserStackLocalTunnel tunnelFor(Map<String, String> args) {
        // Copia propia que admite valores nulos: Map.copyOf los rechaza
        Map<String, String> configuration = new HashMap<>(args);
        configuration.remove(BrowserStackLocalTunnel.LOCAL_IDENTIFIER_ARG);
        return tunnels.computeIfAbsent(Collections.unmodifiableMap(configuration),
                ignored -> tunnelFactory.apply(identifierPrefix + "-" + created.incrementAndGet()));
    }

    /** Host, pid y arranque de la JVM: unico en la cuenta aunque falle el DNS o se repita el pid. */
    static String identifierPrefix(String host, long pid, Instant jvmStart) {
        String safeHost = StringUtils.isBlank(host) ? "host" : host.replaceAll("[^A-Za-z0-9-]", "-");
        return "zahori-" + StringUtils.left(safeHost, MAX_HOST_LENGTH) + "-" + pid + "-" + jvmStart.getEpochSecond();
    }

    interface HostLookup {
        String hostName() throws UnknownHostException;
    }

    static String localHostName(HostLookup lookup) {
        try {
            return lookup.hostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
