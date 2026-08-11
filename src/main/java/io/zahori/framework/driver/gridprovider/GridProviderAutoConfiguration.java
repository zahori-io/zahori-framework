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
import io.zahori.framework.spring.SpringContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the framework's built-in {@link GridProvider}s (and the {@link SpringContextHolder}
 * bridge) in any Spring Boot process consuming zahori-framework, regardless of that process's own
 * component-scan base package — see
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * <p>
 * A consuming process that needs a grid provider this framework doesn't ship (SauceLabs,
 * LambdaTest, a private grid...) simply declares its own {@code @Component implements
 * GridProvider} in its own code — Spring collects it into the same {@code List<GridProvider>}
 * as these built-in ones, no change required here.
 */
@Configuration
public class GridProviderAutoConfiguration {

    @Bean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    public SelenoidGridProvider selenoidGridProvider() {
        return new SelenoidGridProvider();
    }

    @Bean
    public BrowserStackGridProvider browserStackGridProvider() {
        return new BrowserStackGridProvider();
    }
}
