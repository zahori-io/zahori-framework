package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.DesiredCapabilities;

public class CapabilitiesBuilder {

    public static void main(String[] args) {
        // TEST
        Browsers browsers = new Browsers().withName("chrome").withBits("64").withPlatform("iOS")
                    .withVersion("").withScreenResolution("1920x1080x24").withRemote("YES")
                    .withTestName("TC Name").withRemoteUrl("https://xxx:4444/wd").withCaseExecution("TC Id")
                    .withExecution(1234L)
                    .withEnvironmentUrl("https://prod.domain.com")
                    .withEnvironmentName("Pro iOS");
        System.out.println("Capabilities: " + getCapabilitiesWithPrefix("ios.", browsers));
        
        //System.out.println("Capabilities: " + getCapabilities());
    }

    public static DesiredCapabilities getCapabilities() {
        return getCapabilitiesWithPrefix(null, null);
    }

    public static DesiredCapabilities getCapabilitiesWithPrefix(String prefix, Browsers browsers) {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        try {
            Map<String, String> extraCapabilities = new ZahoriProperties().getExtraCapabilities();

            // Crear un mapa para gestionar las propiedades dinámicamente
            Map<String, Object> capabilityMap = new HashMap<>();

            // Iterar sobre todas las claves del archivo properties
            // for (String key : properties.stringPropertyNames()) {
            for (Map.Entry<String, String> entry : extraCapabilities.entrySet()) {
                String key = entry.getKey();
                String value = browsers != null ? getCapabilityValue(browsers, entry.getValue()) : entry.getValue();

                if (StringUtils.isNotBlank(prefix)) {
                    if (key.startsWith(prefix)) {
                        key = key.replaceFirst(prefix, StringUtils.EMPTY);
                    } else {
                        continue;
                    }
                }

                // Separar la clave por los puntos (.) para determinar la jerarquía
                String[] keyParts = key.split("\\.");

                // Añadir la propiedad al mapa de capacidades dinámicamente
                addToMap(capabilityMap, keyParts, value);
            }

            // Añadir las propiedades a las DesiredCapabilities de Selenium
            for (Map.Entry<String, Object> entry : capabilityMap.entrySet()) {
                capabilities.setCapability(entry.getKey(), entry.getValue());
            }

            return capabilities;
        } catch (Exception e) {
            throw new RuntimeException("Error reading capabilities from zahori.properties: " + e.getMessage());
        }
    }

    private static String getCapabilityValue(Browsers browsers, String capabilityValue) {
        if (browsers == null) {
            return capabilityValue;
        }

        if (StringUtils.contains(capabilityValue, "{platform}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{platform}", browsers.getPlatform().toLowerCase());
        }
        if (StringUtils.contains(capabilityValue, "{executionId}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{executionId}", browsers.getExecutionId().toString());
        }
        if (StringUtils.contains(capabilityValue, "{caseExecutionId}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{caseExecutionId}", browsers.getCaseExecutionId());
        }
        if (StringUtils.contains(capabilityValue, "{caseName}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{caseName}", browsers.getTestName());
        }

        return capabilityValue;
    }

    // Método recursivo para añadir valores a un mapa en función de la jerarquía de las claves
    @SuppressWarnings("unchecked")
    private static void addToMap(Map<String, Object> map, String[] keyParts, Object value) {
        Map<String, Object> currentMap = map;

        // Recorre todas las partes de la clave, menos la última
        for (int i = 0; i < keyParts.length - 1; i++) {
            String part = keyParts[i];

            // Si el mapa actual no contiene esta clave, añadimos un nuevo submapa
            if (!currentMap.containsKey(part)) {
                currentMap.put(part, new HashMap<String, Object>());
            }

            // Avanza al siguiente submapa
            currentMap = (Map<String, Object>) currentMap.get(part);
        }

        // Al llegar a la última parte de la clave, añade el valor
        currentMap.put(keyParts[keyParts.length - 1], parseValue(value));
    }

    // Método para interpretar el valor como Booleano, Número o String según corresponda
    private static Object parseValue(Object value) {
        if (value == null){
            return "";
        }
        
        String strValue = value.toString();
        if (isBoolean(strValue)) {
            return Boolean.valueOf(strValue);
        } else {
            return strValue;
        }
    }

    private static boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }
}
