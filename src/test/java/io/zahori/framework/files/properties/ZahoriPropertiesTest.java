package io.zahori.framework.files.properties;

import io.zahori.framework.core.ExecutionTarget;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ZahoriPropertiesTest {
    @Test
    void noOverlayFiles_behavesExactlyLikeBaseOnly() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("does-not-exist-env", "does-not-exist-platform"));
        Assertions.assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        Assertions.assertThat(props.getProperty("test.key.overridden")).isEqualTo("base-value");
        Assertions.assertThat(props.getProperty("test.key.env-only")).isEmpty();
        Assertions.assertThat(props.getProperty("test.key.platform-only")).isEmpty();
    }

    @Test
    void environmentOverlay_overridesBase() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("STG", null));
        Assertions.assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        Assertions.assertThat(props.getProperty("test.key.overridden")).isEqualTo("stg-value");
        Assertions.assertThat(props.getProperty("test.key.env-only")).isEqualTo("stg-only-value");
        Assertions.assertThat(props.getProperty("test.key.platform-only")).isEmpty();
    }

    @Test
    void environmentOverlay_appliesWhenTheEnvironmentTokenIsNotFirst() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("iOS STG 5.27.0", null));
        Assertions.assertThat(props.getProperty("test.key.overridden")).isEqualTo("stg-value");
        Assertions.assertThat(props.getProperty("test.key.env-only")).isEqualTo("stg-only-value");
    }

    @Test
    void platformOverlay_isMoreSpecific_andWinsOverEnvironmentOverlay() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("STG", "android"));
        Assertions.assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        Assertions.assertThat(props.getProperty("test.key.overridden")).isEqualTo("stg-android-value");
        Assertions.assertThat(props.getProperty("test.key.env-only")).isEqualTo("stg-only-value");
        Assertions.assertThat(props.getProperty("test.key.platform-only")).isEqualTo("stg-android-only-value");
    }

    @Test
    void wholeValueEnvironmentVariable_resolvesFromSystemEnv() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));
        Assertions.assertThat(props.getProperty("test.key.wholevar")).isEqualTo(System.getenv("PATH"));
    }

    @Test
    void wholeValueEnvironmentVariable_unset_resolvesToEmpty() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));
        Assertions.assertThat(props.getProperty("test.key.wholevar-missing")).isEmpty();
    }

    @Test
    void embeddedEnvironmentVariable_resolvesInsideCompoundValue() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));
        String resolved = props.getProperty("test.key.embedded");
        Assertions.assertThat(resolved).doesNotContain(new CharSequence[]{"${PATH}"});
        Assertions.assertThat(resolved).contains(new CharSequence[]{System.getenv("PATH")});
        Assertions.assertThat(resolved).contains(new CharSequence[]{"\"b\":\"static\""});
    }

    @Test
    void embeddedEnvironmentVariable_unset_resolvesToEmptyInPlace() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));
        Assertions.assertThat(props.getProperty("test.key.embedded-missing")).isEqualTo("pre--post");
    }
}
