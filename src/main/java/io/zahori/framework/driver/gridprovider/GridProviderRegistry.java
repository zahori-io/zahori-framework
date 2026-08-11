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
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves which {@link GridProvider} applies to a given remote URL. Not a Spring bean itself —
 * {@link #resolve()} builds one on demand from whatever {@link GridProvider} beans are currently
 * registered in the Spring context (built-in ones from {@code GridProviderAutoConfiguration} plus
 * any 3rd-party ones a consuming process declared), falling back to just the built-in providers
 * when no Spring context is available (e.g. {@code CapabilitiesBuilder}'s standalone
 * {@code main()}, or a plain unit test) — so provider resolution never depends on Spring being up.
 */
public final class GridProviderRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(GridProviderRegistry.class);

    private static final List<GridProvider> BUILT_IN_PROVIDERS = List.of(new SelenoidGridProvider(), new BrowserStackGridProvider());

    private static final GridProvider FALLBACK = new GenericGridProvider();

    private final List<GridProvider> providers;

    public GridProviderRegistry(List<GridProvider> providers) {
        this.providers = providers == null || providers.isEmpty() ? BUILT_IN_PROVIDERS : providers;
    }

    /**
     * Builds a registry from the Spring context's {@link GridProvider} beans, or from the
     * built-in providers alone if no Spring context is available in this JVM.
     */
    public static GridProviderRegistry resolve() {
        return new GridProviderRegistry(SpringContextHolder.getBeansOfType(GridProvider.class));
    }

    /**
     * @param remoteUrl the grid URL this execution is pointed at (e.g. {@code Browsers.remoteUrl})
     * @param autodetectEnabled the {@code zahori.test.execution.gridProvider.autodetect.enabled}
     * kill-switch — when {@code false}, always returns the fallback provider (pre-GridProvider
     * behavior), ignoring {@code remoteUrl} entirely
     */
    public GridProvider resolveProvider(String remoteUrl, boolean autodetectEnabled) {
        if (!autodetectEnabled || StringUtils.isBlank(remoteUrl)) {
            return FALLBACK;
        }

        for (GridProvider provider : providers) {
            if (matchesSafely(provider, remoteUrl)) {
                LOG.info("Grid provider resolved: {} (matched by URL pattern)", provider.id());
                return provider;
            }
        }

        LOG.info("No GridProvider matched '{}' — falling back to '{}' (pre-GridProvider behavior)", remoteUrl, FALLBACK.id());
        return FALLBACK;
    }

    private boolean matchesSafely(GridProvider provider, String remoteUrl) {
        try {
            return provider.matches(remoteUrl);
        } catch (Exception e) {
            LOG.warn("GridProvider '{}' threw while matching '{}': {}", provider.id(), remoteUrl, e.getMessage());
            return false;
        }
    }
}
