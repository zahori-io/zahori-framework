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
import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.List;
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * Models a remote Selenium/Appium grid provider (Selenoid, BrowserStack, and whatever comes
 * next — SauceLabs, LambdaTest, a private grid...) so that provider-specific behavior (extra
 * capabilities, how headers reach the browser) is resolved through this abstraction instead of
 * hardcoded {@code if}/{@code switch} blocks in {@code RemoteDriver}/{@code TestContext}.
 * <p>
 * Implementations are registered as Spring beans (see {@code GridProviderAutoConfiguration}) so
 * that a consuming process can contribute its own {@code GridProvider} — for a grid this
 * framework doesn't know about yet — simply by declaring a {@code @Component} in its own code,
 * with no change required in {@code zahori-framework} itself.
 */
public interface GridProvider {

    /**
     * Short, stable identifier (e.g. "selenoid", "browserstack"). Used in logs/evidences and as
     * the value of the {@code zahori.test.execution.gridProvider.override} escape hatch.
     */
    String id();

    /**
     * Whether {@code remoteUrl} (the {@code Browsers.remoteUrl}/{@code Configuration.environmentUrl}
     * this execution is pointed at) belongs to this provider. Used for automatic resolution —
     * never throws, returns {@code false} on any unexpected input.
     */
    boolean matches(String remoteUrl);

    /**
     * Adds/overrides whatever capabilities this provider always requires, replacing the
     * hardcoded {@code selenoid:options} block that used to run unconditionally in
     * {@code RemoteDriver.getOptions()} regardless of the actual grid. Provider-specific
     * capabilities declared by the user in {@code zahori.properties}
     * ({@code bstack:options.*}, {@code selenoid:options.*}...) are unaffected — those already
     * flow through the existing key-prefix mechanism in {@code CapabilitiesBuilder}.
     */
    void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers);

    /**
     * How this provider expects headers (and, more generally, the local BrowserMobProxy used for
     * headers/blacklist/HAR capture) to reach the browser under test.
     */
    HeaderInjectionStrategy headerInjectionStrategy();

    /**
     * Property keys (as looked up via {@code ZahoriProperties.getProperty(String)}, including any
     * platform prefix the provider cares about — e.g. {@code "android.bstack:options.headerParams"})
     * that MUST resolve to a non-blank value for this provider to work correctly for {@code
     * platform}. Checked once, fail-fast, before the driver is created — see {@code
     * RequiredCapabilitiesValidator} — instead of letting a missing value surface later as a
     * cryptic Selenium/Appium error or an unrelated-looking HTTP failure.
     * <p>
     * Default: none. Built-in providers declare no framework-level requirement of their own —
     * requirements here are meant for providers whose correct operation genuinely depends on a
     * specific property being set (e.g. a 3rd-party provider that always needs an API key
     * capability), not for business-specific values that happen to be needed by one particular
     * consuming process.
     */
    default List<String> requiredCapabilityKeys(String platform) {
        return List.of();
    }
}
