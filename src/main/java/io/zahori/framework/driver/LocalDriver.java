package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2022 PANEL SISTEMAS INFORMATICOS,S.L
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
import io.github.bonigarcia.wdm.WebDriverManager;
import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.Optional;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDriver extends AbstractDriver {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDriver.class);

    /**
     * Sizes the window and checks what the browser actually applied: a window manager may ignore
     * the request, and nothing downstream notices. Evidences would keep reporting the configured
     * resolution while the site serves the layout of the real width — passing against the wrong
     * page instead of failing.
     */
    private void resizeWindow(WebDriver driver, Browsers browsers) {
        String resolution = browsers.getScreenResolution();
        Dimension requested = new Dimension(Integer.parseInt(resolution.split("x")[0]), Integer.parseInt(resolution.split("x")[1]));
        driver.manage().window().setSize(requested);

        Dimension applied = driver.manage().window().getSize();
        sizeMismatchWarning(requested, applied).ifPresent(LOG::warn);
    }

    /**
     * The warning to emit, or empty when the size was honoured. Returned instead of logged so that
     * both the decision and its wording stay observable from a test, without tying it to a backend.
     *
     * @param requested size asked for, from the configured resolution
     * @param applied   size the browser actually reports
     * @return the warning, or {@link Optional#empty()} when both sizes agree
     */
    static Optional<String> sizeMismatchWarning(Dimension requested, Dimension applied) {
        if (!isSizeMismatch(requested, applied)) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                "Requested window size %dx%d but the browser applied %dx%d: the site serves the layout matching "
                        + "the APPLIED width, while evidences report the configured resolution.",
                requested.getWidth(), requested.getHeight(), applied.getWidth(), applied.getHeight()));
    }

    /**
     * Whether the browser ignored the requested size. The width decides which layout the site
     * serves; a height that does not fit hides what has to be clicked.
     *
     * @param requested size asked for, from the configured resolution
     * @param applied   size the browser actually reports
     * @return {@code true} when they differ in either dimension
     */
    static boolean isSizeMismatch(Dimension requested, Dimension applied) {
        return applied.getWidth() != requested.getWidth() || applied.getHeight() != requested.getHeight();
    }

    @Override
    protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
        return WebDriverManager.getInstance(browsers.getName().toUpperCase())
                .capabilities(getOptions(browsers, proxy))
                .create();
    }

    // Si hay configuraciones específicas para LocalDriver, puedes sobrescribir 'configureWebDriver' aquí
    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        resizeWindow(webDriver, browsers);
    }
}
