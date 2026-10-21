package io.zahori.framework.core;

import io.zahori.model.process.CaseExecution;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ClassAssert;
import org.junit.jupiter.api.Test;

class PublicApiContractTest {
    @Test
    void testContextExposesTheLoggingApi() throws NoSuchMethodException {
        assertPublicMethod(TestContext.class, "logInfo", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepPassed", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepPassedWithScreenshot", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepFailed", String.class, String[].class);
        assertPublicMethod(TestContext.class, "logStepFailedWithScreenshot", String.class, String[].class);
    }

    @Test
    void theLoggingApiTakesVarargs() throws NoSuchMethodException {
        for (String name : new String[]{"logInfo", "logStepPassed", "logStepPassedWithScreenshot", "logStepFailed", "logStepFailedWithScreenshot"}) {
            Method method = TestContext.class.getMethod(name, String.class, String[].class);
            ((AbstractBooleanAssert)Assertions.assertThat(method.isVarArgs()).as(name + " must accept variable arguments", new Object[0])).isTrue();
        }
    }

    @Test
    void testContextExposesPlatformDetection() throws NoSuchMethodException {
        assertPublicMethod(TestContext.class, "isMobileNativeApp");
        assertPublicMethod(TestContext.class, "isMobileWebApp");
        assertPublicMethod(TestContext.class, "isAndroidDriver");
        assertPublicMethod(TestContext.class, "isIOSDriver");

        for (String name : new String[]{"isMobileNativeApp", "isMobileWebApp", "isAndroidDriver", "isIOSDriver"}) {
            ((ClassAssert)Assertions.assertThat(TestContext.class.getMethod(name).getReturnType()).as(name + " must return a primitive boolean", new Object[0]))
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
    void testContextKeepsTheBrowserStackTunnelOperations() throws NoSuchMethodException {
        assertPublicMethod(TestContext.class, "startRemoteTunnel");
        assertPublicMethod(TestContext.class, "isTunnelRunning");
        assertPublicMethod(TestContext.class, "getTunnelLocalIdentifier");
    }

    @Test
    void stopRemoteTunnelIsKeptOnlyUntilItsAnnouncedRemoval() throws NoSuchMethodException {
        Deprecated deprecated = TestContext.class.getMethod("stopRemoteTunnel").getAnnotation(Deprecated.class);

        Assertions.assertThat(deprecated).isNotNull();
        Assertions.assertThat(deprecated.forRemoval()).isTrue();
    }

    @Test
    void testContextKeepsTheFieldsConsumersReadDirectly() throws NoSuchFieldException {
        assertPublicField(TestContext.class, "driver");
        assertPublicField(TestContext.class, "timeoutFindElement");
    }

    @Test
    void pageElementKeepsTheConstructorTakingAHumanReadableDescription() {
        boolean found = false;

        for (Constructor<?> constructor : PageElement.class.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length >= 3 && parameters[1] == String.class) {
                found = true;
                break;
            }
        }

        ((AbstractBooleanAssert)Assertions.assertThat(found)
                .as(
                    "PageElement must keep a (page, description, locator...) constructor: the description is what shows up in the client's evidence",
                    new Object[0]
                ))
            .isTrue();
    }

    @Test
    void pageElementExposesTheWaitsThatReplaceThreadSleep() {
        assertHasMethod(PageElement.class, "isVisible");
        assertHasMethod(PageElement.class, "click");
        assertHasMethod(PageElement.class, "getText");
        assertHasMethod(PageElement.class, "initWebElement");
        assertHasMethod(PageElement.class, "tryInitWebElement");
    }

    @Test
    void locatorKeepsItsStaticFactories() {
        assertHasStaticMethod(Locator.class, "xpath");
    }

    @Test
    void baseProcessKeepsTheExtensionPoint() throws NoSuchMethodException {
        Method run = BaseProcess.class.getDeclaredMethod("run", TestContext.class, CaseExecution.class);
        ((AbstractBooleanAssert)Assertions.assertThat(Modifier.isAbstract(run.getModifiers()))
                .as("run must stay abstract: it is what forces every consumer to implement it", new Object[0]))
            .isTrue();
        ((AbstractBooleanAssert)Assertions.assertThat(Modifier.isPrivate(run.getModifiers())).as("run cannot be private: consumers override it", new Object[0]))
            .isFalse();
        ((ClassAssert)Assertions.assertThat(run.getParameterTypes()[0]).as("the first parameter of run must be TestContext", new Object[0]))
            .isEqualTo(TestContext.class);
    }

    private static void assertPublicMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        ((AbstractBooleanAssert)Assertions.assertThat(Modifier.isPublic(method.getModifiers())).as(name + " must be public", new Object[0])).isTrue();
    }

    private static void assertPublicField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getField(name);
        ((AbstractBooleanAssert)Assertions.assertThat(Modifier.isPublic(field.getModifiers()))
                .as(name + " must stay a publicly accessible field", new Object[0]))
            .isTrue();
    }

    private static void assertHasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return;
            }
        }

        Assertions.fail(type.getSimpleName() + " must keep the public method '" + name + "'");
    }

    private static void assertHasStaticMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())) {
                return;
            }
        }

        Assertions.fail(type.getSimpleName() + " must keep the static factory '" + name + "'");
    }
}
