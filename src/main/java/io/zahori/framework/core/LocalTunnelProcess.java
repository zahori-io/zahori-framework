package io.zahori.framework.core;

import java.util.Map;

/** Lo que el tunel necesita del binario BrowserStack Local. */
interface LocalTunnelProcess {

    /** Bloquea hasta que el daemon conecta; si no conecta, lanza. */
    void start(Map<String, String> args) throws Exception;

    boolean isAlive();

    /** @return pid del daemon, o 0 si no conecto */
    long pid();

    void stop() throws Exception;

    /** Para el daemon por su identificador, aunque start() no llegara a darle pid. */
    void stopByIdentifier(Map<String, String> args) throws Exception;

    /** Mata lo que este lanzando y no deja lanzar nada mas. */
    void abort();
}
