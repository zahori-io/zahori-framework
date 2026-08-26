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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * Contract of the public API that consumer projects actually use.
 *
 * <p><b>Why it exists.</b> zahori-framework is not one client's code: it is an OSS product
 * published to Maven Central, and its consumers live in OTHER repositories, most of which we never
 * see. Breaking a public signature therefore <em>compiles perfectly here</em> and blows up in the
 * consumer weeks later, when somebody bumps the version.
 *
 * <p>This test is the net that was missing: it pins the surface consumers rely on TODAY, so that
 * breaking it turns something red <b>before</b> publishing rather than after.
 *
 * <p><b>How the surface was chosen.</b> Not from memory nor by taste: it was measured over the real
 * code of the two consumers in this workspace (import and call counts, 2026-08-11). The numbers in
 * each block comment are that count.
 *
 * <p><b>What to do when this test goes red.</b> Do not "fix" it by adjusting the test. A failure
 * here means you are about to break a consumer. The legitimate ways out, in order of preference:
 *
 * <ol>
 *   <li><b>Overload</b>: add the new signature and keep the old one.</li>
 *   <li><b>Deprecate</b>: mark the old one {@code @Deprecated} and let it live long enough for
 *       consumers to migrate.</li>
 *   <li><b>Break it on purpose</b>: only as an explicit decision, bumping the version accordingly
 *       and warning the consumers. Then — and only then — this test is updated, in the same commit
 *       as the change.</li>
 * </ol>
 *
 * <p>This test checks the <b>presence and shape</b> of the API, not its behaviour. It starts no
 * browser and needs no environment: pure reflection, milliseconds, and it cannot turn flaky.
 */
class PublicApiContractTest {

    // ---------------------------------------------------------------- TestContext
    // 218 imports across the consumers: the most coupled class in the framework.

    @Test
    void testContextExposesTheLoggingApi() throws NoSuchMethodException {
        // 235 + 181 + 176 + 40 + 10 measured calls. If anything breaks a consumer, it is this.
        assertPublicMethod(TestContext.class, "logInfo", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepPassed", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepPassedWithScreenshot", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepFailed", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepFailedWithScreenshot", String.class, String[].class);
    }

    @Test
    void theLoggingApiTakesVarargs() throws NoSuchMethodException {
        // Consumers call logInfo("...{}...", value). Dropping the varargs would break 600+ call
        // sites without this module failing to compile.
        for (String name : new String[] {
            "logInfo", "logStepPassed", "logStepPassedWithScreenshot",
            "logStepFailed", "logStepFailedWithScreenshot"
        }) {
            Method method = TestContext.class.getMethod(name, String.class, String[].class);
            assertThat(method.isVarArgs()).as(name + " must accept variable arguments").isTrue();
        }
    }

    @Test
    void testContextExposesPlatformDetection() throws NoSuchMethodException {
        // 54 + 40 + 30 + 17 calls: this is how consumers branch Web/Android/iOS.
        assertPublicMethod(TestContext.class, "isMobileNativeApp");
        assertPublicMethod(TestContext.class, "isMobileWebApp");
        assertPublicMethod(TestContext.class, "isAndroidDriver");
        assertPublicMethod(TestContext.class, "isIOSDriver");

        for (String name : new String[] {
            "isMobileNativeApp", "isMobileWebApp", "isAndroidDriver", "isIOSDriver"
        }) {
            assertThat(TestContext.class.getMethod(name).getReturnType())
                .as(name + " must return a primitive boolean")
                .isEqualTo(boolean.class);
        }
    }

    @Test
    void testContextExposesTheMobileContextOperations() throws NoSuchMethodException {
        assertPublicMethod(TestContext.class, "switchToNativeContext");
        assertPublicMethod(TestContext.class, "hideKeyboard");
        assertPublicMethod(TestContext.class, "getBrowser");
    }

    @Test
    void testContextKeepsTheFieldsConsumersReadDirectly() throws NoSuchFieldException {
        // 82 uses of testContext.driver and 33 of timeoutFindElement. Encapsulating them is
        // desirable, but it cannot be done silently: they are public API de facto. If they ever get
        // encapsulated, the getter has to arrive first and the field has to be deprecated, not
        // removed.
        assertPublicField(TestContext.class, "driver");
        assertPublicField(TestContext.class, "timeoutFindElement");
    }

    // ------------------------------------------------------- Page / PageElement / Locator
    // 125 + 124 + 82 imports.

    @Test
    void pageElementKeepsTheConstructorTakingAHumanReadableDescription() {
        // The description is the second argument and ends up in the evidence the client reads. A
        // constructor without it would leave those reports unreadable.
        boolean found = false;
        for (Constructor<?> constructor : PageElement.class.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length >= 3 && parameters[1] == String.class) {
                found = true;
                break;
            }
        }
        assertThat(found).as("PageElement must keep a (page, description, locator...) constructor: "
            + "the description is what shows up in the client's evidence").isTrue();
    }

    @Test
    void pageElementExposesTheWaitsThatReplaceThreadSleep() {
        // isVisible(timeout) is the sanctioned alternative to a fixed sleep. If it disappears,
        // every consumer that was told not to use Thread.sleep is left without a way out.
        assertHasMethod(PageElement.class, "isVisible");
        assertHasMethod(PageElement.class, "click");
        assertHasMethod(PageElement.class, "getText");
    }

    @Test
    void locatorKeepsItsStaticFactories() {
        // Locator.xpath(...) shows up in practically every page object.
        assertHasStaticMethod(Locator.class, "xpath");
    }

    // ------------------------------------------------------------ ZahoriProcess / BaseProcess

    @Test
    void baseProcessKeepsTheExtensionPoint() throws NoSuchMethodException {
        // The framework's Template Method: BaseProcess declares `run` abstract and every consuming
        // process implements it (a consumer may widen it to public, which Java allows). Changing
        // this signature breaks the entry point of EVERY consumer.
        //
        // It lives in BaseProcess, NOT in ZahoriProcess, and it is `protected` — hence
        // getDeclaredMethod and not getMethods(), which only sees public ones. (This very test
        // found that out by failing: the first version looked for it in the wrong place.)
        Method run = BaseProcess.class.getDeclaredMethod(
            "run", TestContext.class, io.zahori.model.process.CaseExecution.class);

        assertThat(Modifier.isAbstract(run.getModifiers()))
            .as("run must stay abstract: it is what forces every consumer to implement it")
            .isTrue();
        assertThat(Modifier.isPrivate(run.getModifiers()))
            .as("run cannot be private: consumers override it")
            .isFalse();
        assertThat(run.getParameterTypes()[0])
            .as("the first parameter of run must be TestContext")
            .isEqualTo(TestContext.class);
    }

    // ------------------------------------------------------------------------- helpers

    private static void assertPublicMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        assertThat(Modifier.isPublic(method.getModifiers())).as(name + " must be public").isTrue();
    }

    private static void assertPublicField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getField(name);
        assertThat(Modifier.isPublic(field.getModifiers()))
            .as(name + " must stay a publicly accessible field")
            .isTrue();
    }

    private static void assertHasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return;
            }
        }
        fail(type.getSimpleName() + " must keep the public method '" + name + "'");
    }

    private static void assertHasStaticMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())) {
                return;
            }
        }
        fail(type.getSimpleName() + " must keep the static factory '" + name + "'");
    }
}
