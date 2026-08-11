package io.zahori.framework.utils;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Redacts sensitive values (tokens, passwords, auth headers...) before a capabilities map is
 * serialized to a log line. Now that {@code ZahoriProperties.getProperty()} resolves {@code ${VAR}}
 * references embedded inside compound values (e.g. BrowserStack's
 * {@code bstack:options.headerParams} JSON blob), capabilities that used to carry the literal
 * placeholder start carrying the real secret — logging them unmasked would leak it into
 * logs/evidences.
 */
public final class CapabilitiesMasker {

    private static final String MASKED_VALUE = "***MASKED***";

    private static final List<String> SENSITIVE_KEY_FRAGMENTS = List.of(
            "password", "token", "secret", "apikey", "api-key", "api_key", "accesskey", "access-key", "access_key",
            "authorization", "auth", "credential", "headerparams", "header-params", "header_params");

    private CapabilitiesMasker() {
    }

    /**
     * @return a deep copy of {@code capabilities} where every value reachable under a
     * sensitive-looking key (recursing into nested maps, e.g. {@code bstack:options}) has been
     * replaced with a fixed placeholder. Safe to log/serialize.
     */
    public static Map<String, Object> mask(Map<String, ?> capabilities) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (capabilities == null) {
            return masked;
        }

        for (Map.Entry<String, ?> entry : capabilities.entrySet()) {
            masked.put(entry.getKey(), maskValue(entry.getKey(), entry.getValue()));
        }
        return masked;
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return MASKED_VALUE;
        }
        if (value instanceof Map<?, ?> nested) {
            return mask((Map<String, ?>) nested);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String normalizedKey = key.toLowerCase();
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(normalizedKey::contains);
    }
}
