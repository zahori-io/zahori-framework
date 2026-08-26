package io.zahori.framework.core;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/agpl-3.0.html>.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zahori.framework.files.properties.ZahoriProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Contract of the per-platform remote grid URL override
 * ({@code zahori.test.execution.remoteUrl.<platform>}).
 *
 * <p>Why it exists: a Zahori process gets a single grid URL injected at Spring boot, so every
 * platform of that process is forced onto the same grid. That is what made "Android and iOS on a
 * cloud grid, Windows and OSX on the local Selenoid" impossible without commenting and uncommenting
 * zahori.properties by hand before each run — a change that is neither versionable nor reviewable,
 * and that is trivial to forget to revert.
 *
 * <p>The riskiest property of this change is not that the override works: it is that <b>everything
 * keeps working when the override is absent</b>. There are live consumer projects that know nothing
 * about this property, and the framework is published to Maven Central. Half of these tests are
 * about that.
 */
class TestContextRemoteUrlTest {

    private static final String INJECTED = "http://localhost:4444/wd/hub";
    private static final String BROWSERSTACK = "http://hub.browserstack.com/wd/hub";

    private static ZahoriProperties propertiesWith(String key, String value) {
        ZahoriProperties zahoriProperties = mock(ZahoriProperties.class);
        when(zahoriProperties.getProperty(key)).thenReturn(value);
        return zahoriProperties;
    }

    @Test
    void thePlatformOverrideWinsOverTheInjectedUrl() {
        ZahoriProperties properties = propertiesWith("zahori.test.execution.remoteUrl.android", BROWSERSTACK);

        assertEquals(BROWSERSTACK, TestContext.resolveRemoteUrl(INJECTED, "ANDROID", properties));
    }

    /**
     * getPlatform() devuelve "ANDROID" e "IOS" pero "windows" y "osx": la clave de la property no
     * puede depender de esa inconsistencia. Si alguien quita el lowerCase, Android e iOS dejan de
     * encontrar su override y se van al grid equivocado sin fallar.
     */
    @ParameterizedTest
    @CsvSource({
        "ANDROID, zahori.test.execution.remoteUrl.android",
        "IOS,     zahori.test.execution.remoteUrl.ios",
        "windows, zahori.test.execution.remoteUrl.windows",
        "osx,     zahori.test.execution.remoteUrl.osx",
        "LINUX,   zahori.test.execution.remoteUrl.linux"
    })
    void thePropertyKeyDoesNotDependOnThePlatformCasing(String platform, String expectedKey) {
        ZahoriProperties properties = propertiesWith(expectedKey, BROWSERSTACK);

        assertEquals(BROWSERSTACK, TestContext.resolveRemoteUrl(INJECTED, platform, properties));
    }

    // ------------------------------------------------- retrocompatibilidad

    @Test
    void withNoOverrideDeclaredTheInjectedUrlIsKept() {
        ZahoriProperties properties = mock(ZahoriProperties.class);

        assertEquals(INJECTED, TestContext.resolveRemoteUrl(INJECTED, "windows", properties));
    }

    /**
     * Un valor en blanco es "no declarado", no "sin grid". Tratarlo al reves dejaria el proceso sin
     * URL de grid por una linea vacia en el properties, y el fallo aparecería al crear el driver,
     * lejos de la causa.
     */
    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void aBlankValueDoesNotWipeTheInjectedUrl(String blank) {
        ZahoriProperties properties = propertiesWith("zahori.test.execution.remoteUrl.windows", blank);

        assertEquals(INJECTED, TestContext.resolveRemoteUrl(INJECTED, "windows", properties));
    }

    @Test
    void aNullPlatformDoesNotBlowUpAndReturnsTheInjectedUrl() {
        ZahoriProperties properties = mock(ZahoriProperties.class);

        assertEquals(INJECTED, TestContext.resolveRemoteUrl(INJECTED, null, properties));
    }

    /**
     * El override no inventa una URL donde no la habia: si el proceso arranco sin grid remoto y no
     * hay override, sigue sin grid (ejecucion local), no con una cadena vacia que confundiria al
     * GridProviderRegistry.
     */
    @Test
    void withNeitherInjectedUrlNorOverrideNoneIsInvented() {
        ZahoriProperties properties = mock(ZahoriProperties.class);

        assertNull(TestContext.resolveRemoteUrl(null, "windows", properties));
    }

    @Test
    void withNoInjectedUrlTheOverrideStillApplies() {
        ZahoriProperties properties = propertiesWith("zahori.test.execution.remoteUrl.ios", BROWSERSTACK);

        assertEquals(BROWSERSTACK, TestContext.resolveRemoteUrl(null, "IOS", properties));
    }

    /**
     * Cada plataforma lee SU clave: declarar el override de Android no puede desviar una ejecucion
     * de Windows. Es el escenario completo de la Fase 1 — dos grids a la vez en el mismo proceso.
     */
    @Test
    void eachPlatformReadsItsOwnKey() {
        ZahoriProperties properties = mock(ZahoriProperties.class);
        when(properties.getProperty("zahori.test.execution.remoteUrl.android")).thenReturn(BROWSERSTACK);
        when(properties.getProperty("zahori.test.execution.remoteUrl.ios")).thenReturn(BROWSERSTACK);

        assertEquals(BROWSERSTACK, TestContext.resolveRemoteUrl(INJECTED, "ANDROID", properties));
        assertEquals(BROWSERSTACK, TestContext.resolveRemoteUrl(INJECTED, "IOS", properties));
        assertEquals(INJECTED, TestContext.resolveRemoteUrl(INJECTED, "windows", properties));
        assertEquals(INJECTED, TestContext.resolveRemoteUrl(INJECTED, "osx", properties));
    }
}
