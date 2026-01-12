package io.zahori.framework.utils.selenium4;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.HasAuthentication;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.virtualauthenticator.Credential;
import org.openqa.selenium.virtualauthenticator.HasVirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticatorOptions;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticatorOptions.Protocol;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticatorOptions.Transport;

/**
 * Utilidades para WebAuthn Virtual Authenticator API (Selenium 4).
 *
 * <p>WebAuthn (Web Authentication API) permite autenticación sin contraseña usando
 * biometría (TouchID, FaceID, Windows Hello) o llaves de seguridad físicas (YubiKey).
 * Esta clase proporciona autenticadores virtuales para testing sin hardware real.
 *
 * <h2>Conceptos clave:</h2>
 * <ul>
 *   <li><b>Authenticator</b>: Dispositivo que genera/almacena credenciales (físico o virtual)</li>
 *   <li><b>Platform Authenticator</b>: Integrado en el dispositivo (TouchID, Windows Hello)</li>
 *   <li><b>Roaming Authenticator</b>: Externo, portátil (USB key, NFC, Bluetooth)</li>
 *   <li><b>Resident Key</b>: Credencial almacenada en el autenticador (discoverable)</li>
 *   <li><b>User Verification</b>: PIN, biometría, etc.</li>
 * </ul>
 *
 * <h2>Ejemplo básico - Test de registro WebAuthn:</h2>
 * <pre>{@code
 * // Crear autenticador virtual tipo plataforma
 * VirtualAuthenticator authenticator = VirtualAuthenticatorUtils
 *     .createPlatformAuthenticator(driver);
 *
 * // Navegar y ejecutar registro
 * driver.get("https://example.com/webauthn/register");
 * registerButton.click();
 *
 * // Verificar credencial creada
 * List<Credential> credentials = VirtualAuthenticatorUtils.getCredentials(authenticator);
 * assertEquals(1, credentials.size());
 *
 * // Limpiar
 * VirtualAuthenticatorUtils.removeAuthenticator(driver, authenticator);
 * }</pre>
 *
 * <h2>Ejemplo - Test de login con credencial pre-registrada:</h2>
 * <pre>{@code
 * // Crear autenticador y añadir credencial existente
 * VirtualAuthenticator auth = VirtualAuthenticatorUtils.createUSBSecurityKey(driver);
 * Credential credential = VirtualAuthenticatorUtils.createResidentCredential(
 *     "example.com",
 *     new byte[]{1,2,3,4}  // user handle
 * );
 * VirtualAuthenticatorUtils.addCredential(auth, credential);
 *
 * // Login
 * driver.get("https://example.com/login");
 * loginWithWebAuthnButton.click();
 *
 * // Verificar login exitoso
 * assertTrue(driver.getCurrentUrl().contains("/dashboard"));
 * }</pre>
 *
 * <h2>Navegadores soportados:</h2>
 * <ul>
 *   <li>Chrome 67+</li>
 *   <li>Edge 79+</li>
 *   <li>Firefox: No soportado vía Selenium (WebDriver limitation)</li>
 *   <li>Safari: No soportado</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/webauthn-2/">W3C WebAuthn Specification</a>
 * @see <a href="https://www.selenium.dev/documentation/webdriver/interactions/virtual_authenticator/">Selenium Virtual Authenticator</a>
 */
public final class VirtualAuthenticatorUtils {

    private static final Logger LOG = LogManager.getLogger(VirtualAuthenticatorUtils.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VirtualAuthenticatorUtils() {
        // Utility class
    }

    // ==================== Authenticator Creation ====================

    /**
     * Crea un Virtual Authenticator con opciones completas.
     *
     * @param driver WebDriver (debe implementar HasVirtualAuthenticator)
     * @param protocol protocolo CTAP (CTAP2 recomendado, U2F para legacy)
     * @param transport tipo de transporte (USB, NFC, BLE, INTERNAL)
     * @param hasResidentKey true para credenciales almacenables (discoverable)
     * @param hasUserVerification true si soporta verificación de usuario
     * @param isUserVerified true si el usuario ya está verificado
     * @return VirtualAuthenticator configurado
     * @throws UnsupportedOperationException si el driver no soporta Virtual Authenticator
     */
    public static VirtualAuthenticator createAuthenticator(
            WebDriver driver,
            Protocol protocol,
            Transport transport,
            boolean hasResidentKey,
            boolean hasUserVerification,
            boolean isUserVerified) {

        if (!supportsVirtualAuthenticator(driver)) {
            throw new UnsupportedOperationException(
                    "El driver no soporta Virtual Authenticator: " + driver.getClass().getSimpleName());
        }

        VirtualAuthenticatorOptions options = new VirtualAuthenticatorOptions()
                .setProtocol(protocol)
                .setTransport(transport)
                .setHasResidentKey(hasResidentKey)
                .setHasUserVerification(hasUserVerification)
                .setIsUserVerified(isUserVerified);

        HasVirtualAuthenticator hvAuth = (HasVirtualAuthenticator) driver;
        VirtualAuthenticator authenticator = hvAuth.addVirtualAuthenticator(options);

        LOG.info("Virtual Authenticator creado: protocol={}, transport={}, residentKey={}, userVerification={}",
                protocol, transport, hasResidentKey, hasUserVerification);

        return authenticator;
    }

    /**
     * Crea un Platform Authenticator (TouchID, FaceID, Windows Hello).
     * Ideal para testing de autenticación biométrica integrada.
     *
     * @param driver WebDriver
     * @return VirtualAuthenticator tipo plataforma
     */
    public static VirtualAuthenticator createPlatformAuthenticator(WebDriver driver) {
        return createAuthenticator(
                driver,
                Protocol.CTAP2,
                Transport.INTERNAL,
                true,   // Resident keys (discoverable credentials)
                true,   // User verification disponible
                true    // Usuario ya verificado (simula biometría exitosa)
        );
    }

    /**
     * Crea un USB Security Key (YubiKey, Titan, etc.).
     * Ideal para testing de llaves de seguridad físicas.
     *
     * @param driver WebDriver
     * @return VirtualAuthenticator tipo USB
     */
    public static VirtualAuthenticator createUSBSecurityKey(WebDriver driver) {
        return createAuthenticator(
                driver,
                Protocol.CTAP2,
                Transport.USB,
                true,   // Resident keys
                true,   // User verification (PIN)
                true    // PIN ya introducido
        );
    }

    /**
     * Crea un NFC Security Key.
     * Ideal para testing de llaves NFC.
     *
     * @param driver WebDriver
     * @return VirtualAuthenticator tipo NFC
     */
    public static VirtualAuthenticator createNFCSecurityKey(WebDriver driver) {
        return createAuthenticator(
                driver,
                Protocol.CTAP2,
                Transport.NFC,
                true,
                true,
                true
        );
    }

    /**
     * Crea un Bluetooth Security Key.
     *
     * @param driver WebDriver
     * @return VirtualAuthenticator tipo BLE
     */
    public static VirtualAuthenticator createBLESecurityKey(WebDriver driver) {
        return createAuthenticator(
                driver,
                Protocol.CTAP2,
                Transport.BLE,
                true,
                true,
                true
        );
    }

    /**
     * Crea un autenticador U2F legacy (FIDO U2F, primera generación).
     * Solo para testing de compatibilidad con sistemas legacy.
     *
     * @param driver WebDriver
     * @return VirtualAuthenticator U2F
     */
    public static VirtualAuthenticator createU2FAuthenticator(WebDriver driver) {
        return createAuthenticator(
                driver,
                Protocol.U2F,
                Transport.USB,
                false,  // U2F no soporta resident keys
                false,  // U2F no tiene user verification
                false
        );
    }

    /**
     * Crea un autenticador sin verificación de usuario.
     * Para testing de flujos que no requieren PIN/biometría.
     *
     * @param driver WebDriver
     * @param transport tipo de transporte
     * @return VirtualAuthenticator sin user verification
     */
    public static VirtualAuthenticator createAuthenticatorWithoutUserVerification(
            WebDriver driver, Transport transport) {
        return createAuthenticator(
                driver,
                Protocol.CTAP2,
                transport,
                true,
                false,  // Sin user verification
                false
        );
    }

    // ==================== Credential Management ====================

    /**
     * Añade una credencial al autenticador virtual.
     *
     * @param authenticator autenticador virtual
     * @param credential credencial a añadir
     */
    public static void addCredential(VirtualAuthenticator authenticator, Credential credential) {
        authenticator.addCredential(credential);
        LOG.info("Credencial añadida al autenticador: rpId={}", credential.getRpId());
    }

    /**
     * Obtiene todas las credenciales del autenticador.
     *
     * @param authenticator autenticador virtual
     * @return lista de credenciales
     */
    public static List<Credential> getCredentials(VirtualAuthenticator authenticator) {
        List<Credential> credentials = authenticator.getCredentials();
        LOG.debug("Credenciales en autenticador: {}", credentials.size());
        return credentials;
    }

    /**
     * Busca una credencial por rpId (Relying Party ID).
     *
     * @param authenticator autenticador virtual
     * @param rpId identificador del Relying Party (dominio)
     * @return Optional con la credencial si existe
     */
    public static Optional<Credential> findCredentialByRpId(
            VirtualAuthenticator authenticator, String rpId) {
        return authenticator.getCredentials().stream()
                .filter(c -> rpId.equals(c.getRpId()))
                .findFirst();
    }

    /**
     * Elimina una credencial específica del autenticador.
     *
     * @param authenticator autenticador virtual
     * @param credentialId ID de la credencial (bytes)
     */
    public static void removeCredential(VirtualAuthenticator authenticator, byte[] credentialId) {
        authenticator.removeCredential(credentialId);
        LOG.info("Credencial eliminada del autenticador");
    }

    /**
     * Elimina una credencial por su ID en Base64.
     *
     * @param authenticator autenticador virtual
     * @param credentialIdBase64 ID de la credencial en Base64
     */
    public static void removeCredential(VirtualAuthenticator authenticator, String credentialIdBase64) {
        byte[] credentialId = Base64.getDecoder().decode(credentialIdBase64);
        removeCredential(authenticator, credentialId);
    }

    /**
     * Elimina todas las credenciales del autenticador.
     *
     * @param authenticator autenticador virtual
     */
    public static void removeAllCredentials(VirtualAuthenticator authenticator) {
        authenticator.removeAllCredentials();
        LOG.info("Todas las credenciales eliminadas del autenticador");
    }

    // ==================== Credential Creation ====================

    /**
     * Crea una credencial resident (discoverable) con clave generada automáticamente.
     *
     * @param rpId Relying Party ID (dominio, ej: "example.com")
     * @param userHandle identificador del usuario (bytes arbitrarios)
     * @return Credential configurada
     */
    public static Credential createResidentCredential(String rpId, byte[] userHandle) {
        PrivateKey privateKey = generateEC256PrivateKey();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        return createResidentCredential(
                generateCredentialId(),
                rpId,
                userHandle,
                keySpec,
                0  // signCount inicial
        );
    }

    /**
     * Crea una credencial resident (discoverable) con parámetros completos.
     *
     * @param credentialId ID único de la credencial
     * @param rpId Relying Party ID
     * @param userHandle identificador del usuario
     * @param privateKeySpec clave privada EC (P-256) en formato PKCS8
     * @param signCount contador de firmas (para detección de clonación)
     * @return Credential configurada
     */
    public static Credential createResidentCredential(
            byte[] credentialId,
            String rpId,
            byte[] userHandle,
            PKCS8EncodedKeySpec privateKeySpec,
            int signCount) {

        Credential credential = Credential.createResidentCredential(
                credentialId,
                rpId,
                privateKeySpec,
                userHandle,
                signCount
        );

        LOG.debug("Credencial resident creada: rpId={}, credentialId={}",
                rpId, Base64.getEncoder().encodeToString(credentialId));

        return credential;
    }

    /**
     * Crea una credencial non-resident (server-side).
     * La credencial se almacena en el servidor, no en el autenticador.
     *
     * @param rpId Relying Party ID
     * @return Credential non-resident
     */
    public static Credential createNonResidentCredential(String rpId) {
        PrivateKey privateKey = generateEC256PrivateKey();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        return createNonResidentCredential(
                generateCredentialId(),
                rpId,
                keySpec,
                0
        );
    }

    /**
     * Crea una credencial non-resident con parámetros completos.
     *
     * @param credentialId ID único
     * @param rpId Relying Party ID
     * @param privateKeySpec clave privada en formato PKCS8
     * @param signCount contador de firmas
     * @return Credential non-resident
     */
    public static Credential createNonResidentCredential(
            byte[] credentialId,
            String rpId,
            PKCS8EncodedKeySpec privateKeySpec,
            int signCount) {

        Credential credential = Credential.createNonResidentCredential(
                credentialId,
                rpId,
                privateKeySpec,
                signCount
        );

        LOG.debug("Credencial non-resident creada: rpId={}", rpId);

        return credential;
    }

    // ==================== Authenticator Lifecycle ====================

    /**
     * Elimina un autenticador virtual del driver.
     *
     * @param driver WebDriver
     * @param authenticator autenticador a eliminar
     */
    public static void removeAuthenticator(WebDriver driver, VirtualAuthenticator authenticator) {
        if (supportsVirtualAuthenticator(driver)) {
            HasVirtualAuthenticator hvAuth = (HasVirtualAuthenticator) driver;
            hvAuth.removeVirtualAuthenticator(authenticator);
            LOG.info("Virtual Authenticator eliminado");
        }
    }

    // ==================== HTTP Basic Auth (bonus) ====================

    /**
     * Registra autenticación HTTP básica automática.
     * Diferente de WebAuthn - esto es para HTTP 401 challenges.
     *
     * @param driver WebDriver (debe implementar HasAuthentication)
     * @param username usuario
     * @param password contraseña
     */
    public static void registerHttpBasicAuth(WebDriver driver, String username, String password) {
        if (driver instanceof HasAuthentication hasAuth) {
            hasAuth.register(UsernameAndPassword.of(username, password));
            LOG.info("HTTP Basic Auth registrado para usuario: {}", username);
        } else {
            LOG.warn("El driver no soporta HasAuthentication");
        }
    }

    /**
     * Registra autenticación HTTP para un dominio específico.
     *
     * @param driver WebDriver
     * @param domain dominio (ej: "api.example.com")
     * @param username usuario
     * @param password contraseña
     */
    public static void registerHttpBasicAuthForDomain(
            WebDriver driver, String domain, String username, String password) {
        if (driver instanceof HasAuthentication hasAuth) {
            hasAuth.register(
                    uri -> uri.getHost().contains(domain),
                    UsernameAndPassword.of(username, password)
            );
            LOG.info("HTTP Basic Auth registrado para dominio: {}", domain);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Verifica si el driver soporta Virtual Authenticator.
     *
     * @param driver WebDriver
     * @return true si soporta HasVirtualAuthenticator
     */
    public static boolean supportsVirtualAuthenticator(WebDriver driver) {
        return driver instanceof HasVirtualAuthenticator;
    }

    /**
     * Genera un ID de credencial aleatorio (32 bytes).
     *
     * @return bytes aleatorios para credential ID
     */
    public static byte[] generateCredentialId() {
        byte[] credentialId = new byte[32];
        SECURE_RANDOM.nextBytes(credentialId);
        return credentialId;
    }

    /**
     * Genera un user handle aleatorio (64 bytes).
     *
     * @return bytes aleatorios para user handle
     */
    public static byte[] generateUserHandle() {
        byte[] userHandle = new byte[64];
        SECURE_RANDOM.nextBytes(userHandle);
        return userHandle;
    }

    /**
     * Genera una clave privada EC P-256 para WebAuthn.
     *
     * @return PrivateKey EC P-256
     * @throws RuntimeException si falla la generación
     */
    public static PrivateKey generateEC256PrivateKey() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256, SECURE_RANDOM);
            KeyPair keyPair = keyGen.generateKeyPair();
            return keyPair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generando clave EC P-256", e);
        }
    }

    /**
     * Convierte bytes a Base64 URL-safe (para credentialId en JavaScript).
     *
     * @param bytes datos a convertir
     * @return String Base64 URL-safe
     */
    public static String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Convierte Base64 URL-safe a bytes.
     *
     * @param base64Url string Base64 URL-safe
     * @return bytes decodificados
     */
    public static byte[] fromBase64Url(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }

    // ==================== Test Scenarios ====================

    /**
     * Configura un escenario completo de testing WebAuthn.
     * Crea autenticador + credencial pre-registrada.
     *
     * @param driver WebDriver
     * @param rpId dominio del sitio (ej: "example.com")
     * @return VirtualAuthenticator con credencial pre-configurada
     */
    public static VirtualAuthenticator setupTestScenario(WebDriver driver, String rpId) {
        VirtualAuthenticator auth = createPlatformAuthenticator(driver);

        Credential credential = createResidentCredential(rpId, generateUserHandle());
        addCredential(auth, credential);

        LOG.info("Escenario de test WebAuthn configurado para: {}", rpId);
        return auth;
    }

    /**
     * Verifica que se creó exactamente una credencial para el rpId.
     *
     * @param authenticator autenticador virtual
     * @param rpId Relying Party ID esperado
     * @return true si existe exactamente una credencial
     */
    public static boolean verifyCredentialCreated(VirtualAuthenticator authenticator, String rpId) {
        long count = authenticator.getCredentials().stream()
                .filter(c -> rpId.equals(c.getRpId()))
                .count();
        boolean success = count == 1;

        if (success) {
            LOG.info("Verificación exitosa: credencial creada para {}", rpId);
        } else {
            LOG.warn("Verificación fallida: {} credenciales encontradas para {}", count, rpId);
        }

        return success;
    }

    /**
     * Obtiene información de debug del autenticador.
     *
     * @param authenticator autenticador virtual
     * @return String con información del autenticador
     */
    public static String getAuthenticatorInfo(VirtualAuthenticator authenticator) {
        List<Credential> credentials = authenticator.getCredentials();
        StringBuilder info = new StringBuilder();
        info.append("=== Virtual Authenticator Info ===\n");
        info.append("Credentials: ").append(credentials.size()).append("\n");

        for (int i = 0; i < credentials.size(); i++) {
            Credential c = credentials.get(i);
            info.append(String.format("  [%d] rpId=%s, isResident=%s%n",
                    i, c.getRpId(), c.isResidentCredential()));
        }

        return info.toString();
    }
}
