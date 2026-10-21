package io.zahori.framework.core;

import io.zahori.framework.testsupport.RecordingTestContext;
import io.zahori.framework.testsupport.SeleniumDoubles;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestContextDomSnapshotTest {
    private RecordingTestContext context() {
        return RecordingTestContext.forCase("TestContextDomSnapshotTest").withTimeout(1).withDriver(SeleniumDoubles.driver());
    }

    @Test
    void itIsOffByDefault() {
        Assertions.assertThat(this.context().isDomSnapshotAlways()).isFalse();
    }

    @Test
    void itCanBeTurnedOn() {
        RecordingTestContext testContext = this.context();
        testContext.setDomSnapshotAlways(true);
        Assertions.assertThat(testContext.isDomSnapshotAlways()).isTrue();
    }

    @Test
    void itCanBeTurnedOffAgain() {
        RecordingTestContext testContext = this.context();
        testContext.setDomSnapshotAlways(true);
        testContext.setDomSnapshotAlways(false);
        Assertions.assertThat(testContext.isDomSnapshotAlways()).isFalse();
    }

    @Test
    void thePropertyNameIsTheOneTheProjectSets() {
        Assertions.assertThat("zahori.test.evidences.domSnapshot.always").isEqualTo("zahori.test.evidences.domSnapshot.always");
    }
}
