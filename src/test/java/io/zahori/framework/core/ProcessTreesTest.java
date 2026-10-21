package io.zahori.framework.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessTreesTest {

    @Test
    void killsTheProcessAndTheForksItLeaves() {
        StubProcessHandle daemon = new StubProcessHandle(false, true);
        StubProcessHandle fork = new StubProcessHandle(false, true);
        daemon.forks.add(fork);

        ProcessTrees.destroyForcibly(daemon);

        assertThat(daemon.isAlive()).isFalse();
        assertThat(fork.isAlive()).describedAs("muerto el padre ya no se lista: hay que tomarlo antes").isFalse();
    }

    @Test
    void listsTheForksOfALiveProcess() {
        StubProcessHandle daemon = new StubProcessHandle(false, true);
        StubProcessHandle fork = new StubProcessHandle(false, true);
        daemon.forks.add(fork);

        assertThat(ProcessTrees.descendantsOf(daemon)).containsExactly(fork);
    }
}
