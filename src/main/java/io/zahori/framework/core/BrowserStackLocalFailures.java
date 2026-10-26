package io.zahori.framework.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/** Rechazos de sesion en los que BrowserStack dice que el tunel Local no le sirve. */
final class BrowserStackLocalFailures {

    // Automate: la sesion pide local y el tunel con ese identificador no esta conectado
    private static final String NOT_CONNECTED = "[browserstack.local] is set to true but local testing through BrowserStack is not connected";
    // App Automate: el tunel existe pero BrowserStack no llega a traves de el
    private static final String CONNECTION_FAILED = "BROWSERSTACK_LOCAL_CONNECTION_FAILED";

    static final String NOT_CONNECTED_REASON = "BrowserStack reports the tunnel as not connected";
    static final String CONNECTION_FAILED_REASON = "BrowserStack reports BROWSERSTACK_LOCAL_CONNECTION_FAILED";

    private BrowserStackLocalFailures() {
    }

    /** @return motivo corto para la evidencia, o null si el fallo no es del tunel */
    static String reasonOf(Throwable failure) {
        // getThrowableList corta las cadenas de causas circulares
        for (Throwable current : ExceptionUtils.getThrowableList(failure)) {
            String message = current.getMessage();
            if (StringUtils.contains(message, NOT_CONNECTED)) {
                return NOT_CONNECTED_REASON;
            }
            if (StringUtils.contains(message, CONNECTION_FAILED)) {
                return CONNECTION_FAILED_REASON;
            }
        }
        return null;
    }
}
