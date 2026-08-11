package io.zahori.framework.core;

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
import org.apache.commons.lang3.StringUtils;

/**
 * Identifies "where" a test case is running: environment, platform and (once resolved) grid
 * provider. Resolved once per execution, in {@link TestContext#constructor()}, and consumed by
 * everything that today recalculates pieces of this identity on its own ({@code ZahoriProperties}
 * overlay loading, capability enrichment, proxy/header strategy).
 */
public record ExecutionTarget(String environment, String platform, String gridProviderId) {

    /**
     * Builds an ExecutionTarget with normalized environment/platform ids and no grid provider
     * resolved yet.
     *
     * @param environmentName raw environment name (e.g. "STG - Web"), may be blank/null
     * @param platform raw platform (e.g. "ANDROID", "windows"), may be blank/null
     */
    public static ExecutionTarget of(String environmentName, String platform) {
        return new ExecutionTarget(normalize(environmentName), normalize(platform), null);
    }

    /**
     * Same ExecutionTarget with the grid provider id resolved (immutable copy).
     */
    public ExecutionTarget withGridProviderId(String resolvedGridProviderId) {
        return new ExecutionTarget(environment, platform, resolvedGridProviderId);
    }

    /**
     * Normalizes a name to a filename-safe, lower case id: alphanumeric characters only, any
     * other character sequence collapsed to a single dash (e.g. "STG - Web" -&gt; "stg-web").
     */
    static String normalize(String name) {
        if (StringUtils.isBlank(name)) {
            return StringUtils.EMPTY;
        }
        return StringUtils.strip(name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-"), "-");
    }
}
