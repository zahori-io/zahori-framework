package io.zahori.framework.core;

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

import org.junit.jupiter.api.Test;

class ExecutionTargetTest {

    @Test
    void normalizesEnvironmentAndPlatform_toLowerCaseDashSeparated() {
        ExecutionTarget target = ExecutionTarget.of("STG - Web", "ANDROID");

        assertThat(target.environment()).isEqualTo("stg-web");
        assertThat(target.platform()).isEqualTo("android");
        assertThat(target.gridProviderId()).isNull();
    }

    @Test
    void blankOrNullInputs_normalizeToEmptyString() {
        ExecutionTarget target = ExecutionTarget.of(null, "");

        assertThat(target.environment()).isEmpty();
        assertThat(target.platform()).isEmpty();
    }

    @Test
    void withGridProviderId_returnsNewImmutableInstance_sameEnvironmentAndPlatform() {
        ExecutionTarget target = ExecutionTarget.of("STG", "windows");

        ExecutionTarget withProvider = target.withGridProviderId("browserstack");

        assertThat(withProvider.environment()).isEqualTo(target.environment());
        assertThat(withProvider.platform()).isEqualTo(target.platform());
        assertThat(withProvider.gridProviderId()).isEqualTo("browserstack");
        assertThat(target.gridProviderId()).isNull();
    }
}
