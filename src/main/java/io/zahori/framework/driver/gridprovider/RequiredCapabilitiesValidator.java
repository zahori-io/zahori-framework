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
import io.zahori.framework.files.properties.ZahoriProperties;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Fails fast, with an explicit message naming environment/platform/provider and the exact keys
 * missing, instead of letting an incomplete configuration surface later as a cryptic
 * Selenium/Appium error or an unrelated-looking HTTP failure — the single validation point for
 * {@link GridProvider#requiredCapabilityKeys(String)}.
 */
public final class RequiredCapabilitiesValidator {

    private RequiredCapabilitiesValidator() {
    }

    /**
     * @throws IllegalStateException if any key declared by {@code provider.requiredCapabilityKeys(platform)}
     * is not set (blank) in {@code zahoriProperties}
     */
    public static void validate(GridProvider provider, String environment, String platform, ZahoriProperties zahoriProperties) {
        List<String> requiredKeys = provider.requiredCapabilityKeys(platform);
        if (requiredKeys.isEmpty()) {
            return;
        }

        List<String> missingKeys = requiredKeys.stream()
                .filter(key -> StringUtils.isBlank(zahoriProperties.getProperty(key)))
                .toList();

        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required configuration for grid provider '" + provider.id() + "' (environment='" + environment
                            + "', platform='" + platform + "'): " + missingKeys
                            + ". Declare " + (missingKeys.size() == 1 ? "this key" : "these keys")
                            + " in zahori.properties or the relevant zahori-<env>[-<platform>].properties overlay.");
        }
    }
}
