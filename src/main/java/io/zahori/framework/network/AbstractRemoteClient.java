package io.zahori.framework.network;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
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

import io.zahori.framework.exception.MethodException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstract base class for remote file transfer clients (FTP, SCP, SSH).
 * Provides common functionality for connection management and file operations.
 */
public abstract class AbstractRemoteClient {

    protected static final Logger LOG = LogManager.getLogger(AbstractRemoteClient.class);
    protected static final String ERROR_UPLOAD = "Error al subir el fichero. Causa: ";
    protected static final String ERROR_DOWNLOAD = "Error al descargar el fichero. Causa: ";
    protected static final String ERROR_TRANSFER = "Error al subir/descargar el fichero. Causa: ";

    protected final String server;
    protected final String user;
    protected final String password;
    protected final int port;

    /**
     * Transfer operation mode.
     */
    protected enum TransferMode {
        UPLOAD, DOWNLOAD
    }

    /**
     * Creates a remote client with default port.
     */
    protected AbstractRemoteClient(String server, String user, String password) {
        this(server, user, password, getDefaultPortStatic());
    }

    /**
     * Creates a remote client with custom port.
     */
    protected AbstractRemoteClient(String server, String user, String password, int port) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = port;
    }

    /**
     * Returns the default port for the protocol.
     * Override in subclasses (FTP=21, SSH/SCP=22).
     */
    protected abstract int getDefaultPort();

    /**
     * Static method to get default port - needed for constructor chaining.
     * Subclasses should override getDefaultPort() instead.
     */
    private static int getDefaultPortStatic() {
        return 22; // Default to SSH port
    }

    /**
     * Builds the full path from directory and file name.
     */
    protected String buildPath(String dir, String file) {
        return StringUtils.isBlank(dir) ? file : dir + "/" + file;
    }

    /**
     * Executes an operation with timing and logging.
     *
     * @param operationName name for logging
     * @param operation the operation to execute
     * @throws MethodException if operation fails
     */
    protected void executeWithTiming(String operationName, ThrowingRunnable operation) throws MethodException {
        LOG.debug("Starting {}", operationName);
        long startTime = System.currentTimeMillis();
        try {
            operation.run();
            LOG.debug("{} completed in {} ms", operationName, System.currentTimeMillis() - startTime);
        } catch (MethodException e) {
            throw e;
        } catch (Exception e) {
            throw new MethodException(ERROR_TRANSFER + e.getMessage(), e);
        }
    }

    /**
     * Functional interface for operations that can throw exceptions.
     */
    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Safely closes a resource, logging any errors.
     */
    protected void closeQuietly(AutoCloseable resource, String resourceName) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                LOG.warn("Error closing {}: {}", resourceName, e.getMessage());
            }
        }
    }

    // Getters for subclasses
    public String getServer() {
        return server;
    }

    public String getUser() {
        return user;
    }

    public int getPort() {
        return port;
    }
}
