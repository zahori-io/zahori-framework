package io.zahori.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zahori.framework.driver.BrowserStackCapacityGate.Plan;
import io.zahori.framework.driver.BrowserStackCapacityGate.Product;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;

/** Antes de cada peticion de sesion a BrowserStack se pide plaza al plan de la cuenta. */
class RemoteDriverAdmissionTest {

    private static final String QUEUE_FULL = "[BROWSERSTACK_QUEUE_SIZE_EXCEEDED] All parallel tests are currently in use";

    private final AtomicLong clock = new AtomicLong(3_600_000L);
    private final RemoteDriver.Sleeper sleeper = mock(RemoteDriver.Sleeper.class);
    private final RemoteDriverSessionTest.RecordingClients http = new RemoteDriverSessionTest.RecordingClients();
    private final RemoteDriverSessionTest.ScriptedGrid grid = new RemoteDriverSessionTest.ScriptedGrid(clock);
    private RemoteDriver driver;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> clock.addAndGet(invocation.<Duration>getArgument(0).toMillis())).when(sleeper).sleep(any());
        driver = new RemoteDriver(clock::get, sleeper, () -> 1.0, http);
    }

    private WebDriver create(Duration total, RemoteDriver.Admission admission) throws Exception {
        return driver.createSession(grid, new URL("http://grid.test/wd/hub"), new MutableCapabilities(), total,
                Duration.ofMinutes(20), Exception::getMessage, admission);
    }

    @Test
    void theSlotIsAskedForBeforeEveryAttempt() throws Exception {
        grid.failsAtOnce(QUEUE_FULL);
        AtomicInteger asked = new AtomicInteger();

        assertThat(create(Duration.ofHours(1), asked::incrementAndGet)).isSameAs(grid.session);

        assertThat(grid.attempts).isEqualTo(2);
        assertThat(asked.get()).isEqualTo(2);
    }

    @Test
    void theSlotIsAskedForBeforeTheGridIsTouched() throws Exception {
        List<String> order = new ArrayList<>();
        RemoteDriver.Admission admission = () -> order.add("slot with " + grid.attempts + " attempts");

        create(Duration.ofHours(1), admission);

        assertThat(order).containsExactly("slot with 0 attempts");
    }

    @Test
    void theWaitForASlotDoesNotEatTheRetryWindow() throws Exception {
        grid.failsAtOnce(QUEUE_FULL);
        // Cada espera dura 10 minutos y el plazo de creacion es de 5: sin descontarla no habria segundo intento
        RemoteDriver.Admission admission = () -> clock.addAndGet(Duration.ofMinutes(10).toMillis());

        assertThat(create(Duration.ofMinutes(5), admission)).isSameAs(grid.session);

        assertThat(grid.attempts).isEqualTo(2);
    }

    @Test
    void anInterruptedWaitStopsTheCreationAndKeepsTheInterruption() {
        RemoteDriver.Admission admission = () -> {
            throw new InterruptedException();
        };

        assertThatThrownBy(() -> create(Duration.ofHours(1), admission)).isInstanceOf(RuntimeException.class);

        assertThat(grid.attempts).isZero();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void withoutAnAdmissionTheOldSignatureStillWorks() throws Exception {
        assertThat(driver.createSession(grid, new URL("http://grid.test/wd/hub"), new MutableCapabilities(), Duration.ofHours(1),
                Duration.ofMinutes(20), Exception::getMessage)).isSameAs(grid.session);
    }

    // --- que producto y que cuenta se usan

    private final List<String> asked = new ArrayList<>();

    private BrowserStackCapacityGate recordingGate() {
        return new BrowserStackCapacityGate((product, user, key) -> {
            asked.add(product + "/" + user + "/" + key);
            return new Plan(0, 4, 0);
        }, clock::get, duration -> { }, BrowserStackCapacityGate.Settings.DEFAULTS);
    }

    private static Map<String, Object> capabilities(String user, String key, boolean nativeApp) {
        Map<String, Object> options = new HashMap<>();
        if (user != null) {
            options.put("userName", user);
        }
        if (key != null) {
            options.put("accessKey", key);
        }
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("bstack:options", options);
        if (nativeApp) {
            capabilities.put("appium:app", "bs://abc");
        }
        return capabilities;
    }

    @Test
    void aWebSessionAsksTheAutomatePlanOfItsAccount() throws Exception {
        RemoteDriver.browserStackAdmission(true, capabilities("someone", "secret", false), true, Duration.ofHours(1),
                recordingGate()).await();

        assertThat(asked).containsExactly("AUTOMATE/someone/secret");
    }

    @Test
    void aNativeAppSessionAsksTheAppAutomatePlan() throws Exception {
        RemoteDriver.browserStackAdmission(true, capabilities("someone", "secret", true), true, Duration.ofHours(1),
                recordingGate()).await();

        assertThat(asked).containsExactly(Product.APP_AUTOMATE + "/someone/secret");
    }

    @Test
    void anotherGridIsNeverGated() {
        assertThat(RemoteDriver.browserStackAdmission(false, capabilities("someone", "secret", false), true, Duration.ofHours(1),
                recordingGate())).isSameAs(RemoteDriver.NO_ADMISSION);
    }

    @Test
    void aSwitchedOffGateIsNeverUsed() {
        assertThat(RemoteDriver.browserStackAdmission(true, capabilities("someone", "secret", false), false, Duration.ofHours(1),
                recordingGate())).isSameAs(RemoteDriver.NO_ADMISSION);
    }

    @Test
    void withoutTheAccountThereIsNothingToAsk() {
        assertThat(RemoteDriver.browserStackAdmission(true, capabilities(null, "secret", false), true, Duration.ofHours(1),
                recordingGate())).isSameAs(RemoteDriver.NO_ADMISSION);
        assertThat(RemoteDriver.browserStackAdmission(true, capabilities("someone", null, false), true, Duration.ofHours(1),
                recordingGate())).isSameAs(RemoteDriver.NO_ADMISSION);
        assertThat(RemoteDriver.browserStackAdmission(true, new HashMap<>(), true, Duration.ofHours(1), recordingGate()))
                .isSameAs(RemoteDriver.NO_ADMISSION);
        assertThat(RemoteDriver.browserStackAdmission(true, Map.of("bstack:options", "not a map"), true, Duration.ofHours(1),
                recordingGate())).isSameAs(RemoteDriver.NO_ADMISSION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "true", "1", "yes"})
    void theGateIsOnByDefaultAndWhenAsked(String configured) {
        assertThat(RemoteDriver.gateEnabled(configured)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "no", "off"})
    void theGateCanBeSwitchedOff(String configured) {
        assertThat(RemoteDriver.gateEnabled(configured)).isFalse();
    }
}
