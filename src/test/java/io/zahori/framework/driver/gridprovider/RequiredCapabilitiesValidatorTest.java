package io.zahori.framework.driver.gridprovider;

import io.zahori.framework.core.ExecutionTarget;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.util.List;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.AbstractDriverOptions;

class RequiredCapabilitiesValidatorTest {
    private final ZahoriProperties zahoriProperties = new ZahoriProperties(ExecutionTarget.of(null, null));

    @Test
    void noRequiredKeys_neverFails() {
        Assertions.assertThatNoException()
            .isThrownBy(() -> RequiredCapabilitiesValidator.validate(new GenericGridProvider(), "stg", "android", this.zahoriProperties));
    }

    @Test
    void missingRequiredKey_failsFast_withExplicitMessage() {
        GridProvider providerWithRequirement = this.requiring("some.required.key.that.does.not.exist");
        ((AbstractThrowableAssert)Assertions.assertThatThrownBy(
                    () -> RequiredCapabilitiesValidator.validate(providerWithRequirement, "stg", "android", this.zahoriProperties)
                )
                .isInstanceOf(IllegalStateException.class))
            .hasMessageContaining("some.required.key.that.does.not.exist")
            .hasMessageContaining("stg")
            .hasMessageContaining("android")
            .hasMessageContaining("fake-provider-with-requirement");
    }

    @Test
    void presentRequiredKey_doesNotFail() {
        GridProvider providerWithRequirement = this.requiring("test.key.base");
        Assertions.assertThatNoException()
            .isThrownBy(() -> RequiredCapabilitiesValidator.validate(providerWithRequirement, "stg", "android", this.zahoriProperties));
    }

    private GridProvider requiring(final String requiredKey) {
        return new GridProvider() {
            public String id() {
                return "fake-provider-with-requirement";
            }

            public boolean matches(String remoteUrl) {
                return false;
            }

            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
            }

            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }

            public List<String> requiredCapabilityKeys(String platform) {
                return List.of(requiredKey);
            }
        };
    }
}
