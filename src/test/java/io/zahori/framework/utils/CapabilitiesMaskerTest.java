package io.zahori.framework.utils;

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

import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilitiesMaskerTest {

    @Test
    void masksTopLevelSensitiveKey() {
        Map<String, Object> masked = CapabilitiesMasker.mask(Map.of("password", "s3cr3t", "browserName", "chrome"));

        assertThat(masked).containsEntry("password", "***MASKED***");
        assertThat(masked).containsEntry("browserName", "chrome");
    }

    @Test
    void masksNestedSensitiveKey_insideProviderOptionsMap() {
        // Regression test for the real leak: bstack:options.headerParams containing resolved
        // secrets once embedded ${VAR} substitution started working.
        Map<String, Object> bstackOptions = Map.of("headerParams", "{\"AEA-AUTHORIZED\":\"real-secret-value\"}", "deviceName", "Pixel 5");
        Map<String, Object> capabilities = Map.of("browserName", "chrome", "bstack:options", bstackOptions);

        Map<String, Object> masked = CapabilitiesMasker.mask(capabilities);

        @SuppressWarnings("unchecked")
        Map<String, Object> maskedBstackOptions = (Map<String, Object>) masked.get("bstack:options");
        assertThat(maskedBstackOptions).containsEntry("headerParams", "***MASKED***");
        assertThat(maskedBstackOptions).containsEntry("deviceName", "Pixel 5");
    }

    @Test
    void isCaseInsensitive() {
        Map<String, Object> masked = CapabilitiesMasker.mask(Map.of("ACCESS_TOKEN", "abc123"));

        assertThat(masked).containsEntry("ACCESS_TOKEN", "***MASKED***");
    }

    @Test
    void doesNotMutateOriginalMap() {
        Map<String, Object> original = Map.of("password", "s3cr3t");

        CapabilitiesMasker.mask(original);

        assertThat(original).containsEntry("password", "s3cr3t");
    }

    @Test
    void handlesNullInput() {
        assertThat(CapabilitiesMasker.mask(null)).isEmpty();
    }
}
