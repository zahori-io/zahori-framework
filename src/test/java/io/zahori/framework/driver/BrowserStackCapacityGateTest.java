package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zahori.framework.driver.BrowserStackCapacityGate.Plan;
import io.zahori.framework.driver.BrowserStackCapacityGate.Product;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** La puerta deja pasar una peticion de sesion solo si el plan de BrowserStack tiene una plaza libre ahora mismo. */
class BrowserStackCapacityGateTest {

    private static final Duration FOREVER = Duration.ofHours(1);

    private final AtomicLong nanos = new AtomicLong();
    private final List<Duration> sleeps = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final Deque<Object> script = new ArrayDeque<>();
    private Plan last = new Plan(0, 4, 0);

    private BrowserStackCapacityGate gate() {
        BrowserStackCapacityGate.PlanSource source = (product, user, key) -> {
            reads.incrementAndGet();
            Object next = script.isEmpty() ? last : script.poll();
            if (next instanceof IOException failure) {
                throw failure;
            }
            last = (Plan) next;
            return last;
        };
        BrowserStackCapacityGate.Sleeper sleeper = duration -> {
            sleeps.add(duration);
            nanos.addAndGet(duration.toNanos());
        };
        return new BrowserStackCapacityGate(source, nanos::get, sleeper, BrowserStackCapacityGate.Settings.DEFAULTS);
    }

    private static void admit(BrowserStackCapacityGate gate) throws InterruptedException {
        gate.admit(Product.AUTOMATE, "user", "key", FOREVER);
    }

    @Test
    void admitsAtOnceWhenThePlanHasAFreeSlot() throws Exception {
        admit(gate());

        assertThat(sleeps).isEmpty();
        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void waitsWhileThePlanIsFullAndAdmitsWhenASlotFrees() throws Exception {
        script.add(new Plan(4, 4, 0));
        script.add(new Plan(4, 4, 0));
        script.add(new Plan(3, 4, 0));

        admit(gate());

        assertThat(sleeps).hasSize(2);
        assertThat(reads.get()).isEqualTo(3);
    }

    @Test
    void aQueuedSessionOfTheAccountIsNotAFreeSlot() throws Exception {
        script.add(new Plan(2, 4, 2));
        script.add(new Plan(2, 4, 1));

        admit(gate());

        assertThat(sleeps).hasSize(1);
    }

    @Test
    void theAdmissionsNotYetVisibleInThePlanCountAsTakenUntilTheySettle() throws Exception {
        BrowserStackCapacityGate gate = gate();
        for (int i = 0; i < 4; i++) {
            admit(gate);
        }
        assertThat(sleeps).isEmpty();

        admit(gate);

        // 3 s de sondeo hasta que las 4 anteriores cumplen los 8 s en que el plan ya las muestra
        assertThat(sleeps).hasSize(3);
        assertThat(nanos.get()).isEqualTo(Duration.ofSeconds(9).toNanos());
    }

    @Test
    void readsThePlanOnlyOnceWithinItsTtl() throws Exception {
        BrowserStackCapacityGate gate = gate();

        admit(gate);
        admit(gate);

        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void failsOpenWhenThePlanCannotBeRead() throws Exception {
        script.add(new IOException("HTTP 503"));
        BrowserStackCapacityGate gate = gate();

        admit(gate);
        admit(gate);

        assertThat(sleeps).isEmpty();
        // Tras un fallo no se vuelve a llamar a la API durante un rato: una caida suya no frena los casos
        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void failsOpenWhenThePlanAnswersNonsense() throws Exception {
        script.add(new Plan(0, 0, 0));

        admit(gate());

        assertThat(sleeps).isEmpty();
    }

    @Test
    void stopsWaitingAtTheMaximumAndLetsTheRequestThrough() throws Exception {
        last = new Plan(4, 4, 0);

        gate().admit(Product.AUTOMATE, "user", "key", Duration.ofSeconds(10));

        assertThat(nanos.get()).isEqualTo(Duration.ofSeconds(10).toNanos());
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3), Duration.ofSeconds(3), Duration.ofSeconds(1));
    }

    @Test
    void eachProductAndAccountHasItsOwnPlan() throws Exception {
        BrowserStackCapacityGate gate = gate();
        for (int i = 0; i < 4; i++) {
            gate.admit(Product.AUTOMATE, "one", "key", FOREVER);
        }

        gate.admit(Product.APP_AUTOMATE, "one", "key", FOREVER);
        gate.admit(Product.AUTOMATE, "two", "key", FOREVER);

        assertThat(sleeps).isEmpty();
    }

    @Test
    void anInterruptionWhileWaitingPropagates() {
        last = new Plan(4, 4, 0);
        BrowserStackCapacityGate gate = new BrowserStackCapacityGate((product, user, key) -> last, nanos::get, duration -> {
            throw new InterruptedException();
        }, BrowserStackCapacityGate.Settings.DEFAULTS);

        assertThatThrownBy(() -> admit(gate)).isInstanceOf(InterruptedException.class);
    }

    // Con el reloj lejos de cero para que una resta cambiada por suma no pase inadvertida
    private static final long LATE = Duration.ofSeconds(100).toNanos();

    @Test
    void theSharedGateIsOneInstance() {
        assertThat(BrowserStackCapacityGate.shared()).isNotNull().isSameAs(BrowserStackCapacityGate.shared());
    }

    @Test
    void afterAFailureThePlanIsReadAgainOnlyOnceTheCooldownIsOver() throws Exception {
        nanos.set(LATE);
        script.add(new IOException("HTTP 503"));
        BrowserStackCapacityGate gate = gate();
        admit(gate);

        nanos.addAndGet(Duration.ofSeconds(30).toNanos() - 1);
        admit(gate);
        assertThat(reads.get()).isEqualTo(1);

        nanos.addAndGet(1);
        admit(gate);
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void thePlanIsReadAgainExactlyWhenItsTtlExpires() throws Exception {
        nanos.set(LATE);
        BrowserStackCapacityGate gate = gate();
        admit(gate);

        nanos.addAndGet(Duration.ofSeconds(3).toNanos() - 1);
        admit(gate);
        assertThat(reads.get()).isEqualTo(1);

        nanos.addAndGet(1);
        admit(gate);
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void anAdmissionStopsCountingExactlyWhenItSettles() throws Exception {
        nanos.set(LATE);
        last = new Plan(0, 1, 0);
        BrowserStackCapacityGate gate = gate();
        admit(gate);

        nanos.addAndGet(Duration.ofSeconds(8).toNanos());
        admit(gate);

        assertThat(sleeps).isEmpty();
    }

    @Test
    void anAdmissionStillCountsBeforeItSettles() throws Exception {
        nanos.set(LATE);
        last = new Plan(0, 1, 0);
        BrowserStackCapacityGate gate = gate();
        admit(gate);

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        admit(gate);

        assertThat(sleeps).isNotEmpty();
    }

    @Test
    void thePlanHasFreeSlotsOnlyWhenRunningAndQueuedLeaveRoom() {
        assertThat(new Plan(0, 4, 0).free()).isEqualTo(4);
        assertThat(new Plan(4, 4, 0).free()).isZero();
        assertThat(new Plan(3, 4, 1).free()).isZero();
        assertThat(new Plan(5, 4, 0).free()).isNegative();
    }
}
