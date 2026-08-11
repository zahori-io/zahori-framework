package io.zahori.framework.spring;

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
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Bridges Spring-managed beans to the parts of the framework that are plain {@code new}-ed
 * objects, not container-managed (e.g. {@code TestContext}, {@code RemoteDriver} — created per
 * test execution, not injected). Registered automatically in any Spring Boot process consuming
 * zahori-framework via {@code GridProviderAutoConfiguration}
 * ({@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}) —
 * not {@code @Component} here directly, since {@code io.zahori.framework} is typically outside a
 * consuming process's own component-scan base package.
 * <p>
 * Outside a Spring context (e.g. {@code CapabilitiesBuilder}'s standalone {@code main()}, or a
 * plain JUnit test that never boots Spring), {@link #getBeansOfType(Class)} simply returns an
 * empty list — callers are expected to fall back to sane defaults in that case, never to fail.
 */
public class SpringContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * @return all beans of {@code type} currently registered in the Spring context, or an empty
     * list if no Spring context has been initialized in this JVM.
     */
    public static <T> List<T> getBeansOfType(Class<T> type) {
        ApplicationContext current = context;
        if (current == null) {
            return List.of();
        }
        return List.copyOf(current.getBeansOfType(type).values());
    }
}
