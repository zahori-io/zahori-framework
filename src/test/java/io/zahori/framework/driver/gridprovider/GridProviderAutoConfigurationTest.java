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

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.spring.SpringContextHolder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proves the extensibility promise of GridProvider (ticket 2/4): a process consuming
 * zahori-framework gets the built-in providers automatically (no @ComponentScan change needed,
 * since io.zahori.framework is normally outside the consuming app's own base package), and can
 * contribute its own provider with a plain @Bean/@Component in ITS OWN configuration — zero
 * change required in zahori-framework itself.
 */
class GridProviderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GridProviderAutoConfiguration.class));

    @Test
    void registersBuiltInProviders_andTheSpringContextHolderBridge() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SelenoidGridProvider.class);
            assertThat(context).hasSingleBean(BrowserStackGridProvider.class);
            assertThat(context).hasSingleBean(SpringContextHolder.class);
            assertThat(context.getBeansOfType(GridProvider.class)).hasSize(2);
        });
    }

    @Test
    void thirdPartyProviderDeclaredOutsideTheFramework_isPickedUpAutomatically_noFrameworkChange() {
        contextRunner.withUserConfiguration(ThirdPartyProviderTestConfig.class).run(context -> {
            List<GridProvider> providers = context.getBeansOfType(GridProvider.class).values().stream().toList();

            assertThat(providers).hasSize(3);
            assertThat(providers).extracting(GridProvider::id).contains("selenoid", "browserstack", "fake-saucelabs");
        });
    }

    @Test
    void springContextHolder_exposesGridProviderBeans_toNonManagedCode() {
        contextRunner.run(context -> {
            // SpringContextHolder.setApplicationContext() is only called once Spring finishes
            // refreshing THIS context; ApplicationContextRunner guarantees that inside run().
            List<GridProvider> providers = SpringContextHolder.getBeansOfType(GridProvider.class);

            assertThat(providers).extracting(GridProvider::id).contains("selenoid", "browserstack");
        });
    }

    @Configuration
    static class ThirdPartyProviderTestConfig {

        @Bean
        GridProvider fakeSaucelabsGridProvider() {
            return new GridProvider() {
                @Override
                public String id() {
                    return "fake-saucelabs";
                }

                @Override
                public boolean matches(String remoteUrl) {
                    return remoteUrl != null && remoteUrl.contains("saucelabs");
                }

                @Override
                public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
                    // no-op
                }

                @Override
                public HeaderInjectionStrategy headerInjectionStrategy() {
                    return HeaderInjectionStrategy.NATIVE_CAPABILITY;
                }
            };
        }
    }
}
