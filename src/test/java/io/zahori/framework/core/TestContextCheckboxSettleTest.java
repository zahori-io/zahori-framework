package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestContextCheckboxSettleTest {
    private RecordingTestContext context() {
        return RecordingTestContext.forCase("TestContextCheckboxSettleTest").withTimeout(60).withDriver(SeleniumDoubles.driver());
    }

    @Test
    void theCheckboxSettleDefaultsToTheProductionValue() {
        Assertions.assertThat(this.context().getCheckboxSettleSeconds()).isEqualTo(5);
    }

    @Test
    void theCheckboxSettleCanBeLoweredForTests() {
        RecordingTestContext testContext = this.context();
        testContext.setCheckboxSettleSeconds(0);
        Assertions.assertThat(testContext.getCheckboxSettleSeconds()).isZero();
    }

    @Test
    void aNegativeCheckboxSettleIsIgnored() {
        RecordingTestContext testContext = this.context();
        testContext.setCheckboxSettleSeconds(3);
        testContext.setCheckboxSettleSeconds(-1);
        Assertions.assertThat(testContext.getCheckboxSettleSeconds()).isEqualTo(3);
    }
}
