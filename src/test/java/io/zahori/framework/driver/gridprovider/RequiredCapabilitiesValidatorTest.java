package io.zahori.framework.driver.gridprovider;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2026 PANEL SISTEMAS INFORMATICOS,S.L
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.zahori.framework.core.ExecutionTarget;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.AbstractDriverOptions;

class RequiredCapabilitiesValidatorTest {

    private final ZahoriProperties zahoriProperties = new ZahoriProperties(ExecutionTarget.of(null, null));

    @Test
    void noRequiredKeys_neverFails() {
        assertThatNoException().isThrownBy(
                () -> RequiredCapabilitiesValidator.validate(new GenericGridProvider(), "stg", "android", zahoriProperties));
    }

    @Test
    void missingRequiredKey_failsFast_withExplicitMessage() {
        GridProvider providerWithRequirement = requiring("some.required.key.that.does.not.exist");

        assertThatThrownBy(() -> RequiredCapabilitiesValidator.validate(providerWithRequirement, "stg", "android", zahoriProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("some.required.key.that.does.not.exist")
                .hasMessageContaining("stg")
                .hasMessageContaining("android")
                .hasMessageContaining("fake-provider-with-requirement");
    }

    @Test
    void presentRequiredKey_doesNotFail() {
        GridProvider providerWithRequirement = requiring("test.key.base"); // set in src/test/resources/zahori.properties

        assertThatNoException().isThrownBy(
                () -> RequiredCapabilitiesValidator.validate(providerWithRequirement, "stg", "android", zahoriProperties));
    }

    private GridProvider requiring(String requiredKey) {
        return new GridProvider() {
            @Override
            public String id() {
                return "fake-provider-with-requirement";
            }

            @Override
            public boolean matches(String remoteUrl) {
                return false;
            }

            @Override
            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
                // no-op
            }

            @Override
            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }

            @Override
            public List<String> requiredCapabilityKeys(String platform) {
                return List.of(requiredKey);
            }
        };
    }
}
