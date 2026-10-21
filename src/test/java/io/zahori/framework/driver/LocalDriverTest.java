package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.Window;

class LocalDriverTest {
    private final LocalDriver driver = new LocalDriver();

    @Test
    void sizesTheWindowToTheConfiguredResolution() {
        Window window = (Window)Mockito.mock(Window.class);
        Mockito.when(window.getSize()).thenReturn(new Dimension(1920, 1080));
        this.driver.configureWebDriver(webDriverShowing(window), new Browsers().withScreenResolution("1920x1080x24"));
        ((Window)Mockito.verify(window)).setSize(new Dimension(1920, 1080));
    }

    @Test
    void readsBackTheSizeTheBrowserActuallyApplied() {
        Window window = (Window)Mockito.mock(Window.class);
        Mockito.when(window.getSize()).thenReturn(new Dimension(400, 842));
        this.driver.configureWebDriver(webDriverShowing(window), new Browsers().withScreenResolution("1920x1080x24"));
        ((Window)Mockito.verify(window)).getSize();
    }

    @Test
    void sizeMatches_whenTheBrowserHonouredTheRequest() {
        Assertions.assertThat(LocalDriver.isSizeMismatch(new Dimension(1920, 1080), new Dimension(1920, 1080))).isFalse();
    }

    @Test
    void sizeMismatches_whenTheWidthDiffers() {
        Assertions.assertThat(LocalDriver.isSizeMismatch(new Dimension(1920, 1080), new Dimension(400, 1080))).isTrue();
    }

    @Test
    void sizeMismatches_whenTheHeightDiffers() {
        Assertions.assertThat(LocalDriver.isSizeMismatch(new Dimension(1920, 1080), new Dimension(1920, 842))).isTrue();
    }

    @Test
    void saysNothing_whenTheBrowserHonouredTheRequest() {
        Assertions.assertThat(LocalDriver.sizeMismatchWarning(new Dimension(1920, 1080), new Dimension(1920, 1080))).isEmpty();
    }

    @Test
    void warnsWithBothSizes_whenTheBrowserIgnoredTheRequest() {
        Assertions.assertThat(LocalDriver.sizeMismatchWarning(new Dimension(1920, 1080), new Dimension(400, 842)))
            .hasValueSatisfying(
                warning -> ((AbstractStringAssert)Assertions.assertThat(warning).contains(new CharSequence[]{"1920x1080"}))
                    .contains(new CharSequence[]{"400x842"})
            );
    }

    private static WebDriver webDriverShowing(Window window) {
        WebDriver webDriver = (WebDriver)Mockito.mock(WebDriver.class);
        Options options = (Options)Mockito.mock(Options.class);
        Mockito.when(webDriver.manage()).thenReturn(options);
        Mockito.when(options.window()).thenReturn(window);
        return webDriver;
    }
}
