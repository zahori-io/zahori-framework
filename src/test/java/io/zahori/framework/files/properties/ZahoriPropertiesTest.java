package io.zahori.framework.files.properties;

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

import io.zahori.framework.core.ExecutionTarget;
import org.junit.jupiter.api.Test;

/**
 * Ticket 1 (ExecutionTarget + overlay cascade) and ticket 3 (embedded ${VAR} substitution)
 * regression coverage. Fixtures live in src/test/resources: zahori.properties (base),
 * zahori-stg.properties (env overlay), zahori-stg-android.properties (env+platform overlay).
 */
class ZahoriPropertiesTest {

    @Test
    void noOverlayFiles_behavesExactlyLikeBaseOnly() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("does-not-exist-env", "does-not-exist-platform"));

        assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        assertThat(props.getProperty("test.key.overridden")).isEqualTo("base-value");
        assertThat(props.getProperty("test.key.env-only")).isEmpty();
        assertThat(props.getProperty("test.key.platform-only")).isEmpty();
    }

    @Test
    void environmentOverlay_overridesBase() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("STG", null));

        assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        assertThat(props.getProperty("test.key.overridden")).isEqualTo("stg-value");
        assertThat(props.getProperty("test.key.env-only")).isEqualTo("stg-only-value");
        assertThat(props.getProperty("test.key.platform-only")).isEmpty();
    }

    @Test
    void platformOverlay_isMoreSpecific_andWinsOverEnvironmentOverlay() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of("STG", "android"));

        assertThat(props.getProperty("test.key.base")).isEqualTo("base-value");
        assertThat(props.getProperty("test.key.overridden")).isEqualTo("stg-android-value");
        assertThat(props.getProperty("test.key.env-only")).isEqualTo("stg-only-value");
        assertThat(props.getProperty("test.key.platform-only")).isEqualTo("stg-android-only-value");
    }

    @Test
    void wholeValueEnvironmentVariable_resolvesFromSystemEnv() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));

        assertThat(props.getProperty("test.key.wholevar")).isEqualTo(System.getenv("PATH"));
    }

    @Test
    void wholeValueEnvironmentVariable_unset_resolvesToEmpty() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));

        assertThat(props.getProperty("test.key.wholevar-missing")).isEmpty();
    }

    @Test
    void embeddedEnvironmentVariable_resolvesInsideCompoundValue() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));

        String resolved = props.getProperty("test.key.embedded");

        assertThat(resolved).doesNotContain("${PATH}");
        assertThat(resolved).contains(System.getenv("PATH"));
        assertThat(resolved).contains("\"b\":\"static\"");
    }

    @Test
    void embeddedEnvironmentVariable_unset_resolvesToEmptyInPlace() {
        ZahoriProperties props = new ZahoriProperties(ExecutionTarget.of(null, null));

        assertThat(props.getProperty("test.key.embedded-missing")).isEqualTo("pre--post");
    }
}
