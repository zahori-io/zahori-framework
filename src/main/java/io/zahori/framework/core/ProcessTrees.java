package io.zahori.framework.core;

import java.util.List;

/** Procesos con sus hijos: en Windows matar al padre no mata a los hijos (el fork del daemon BrowserStackLocal). */
final class ProcessTrees {

    private ProcessTrees() {
    }

    /** Hijos antes de tocar al padre: muerto, el sistema ya no los relaciona con el. */
    static List<ProcessHandle> descendantsOf(ProcessHandle root) {
        return root.descendants().toList();
    }

    static void destroyForcibly(ProcessHandle root) {
        List<ProcessHandle> descendants = descendantsOf(root);
        root.destroyForcibly();
        descendants.forEach(ProcessHandle::destroyForcibly);
    }
}
