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

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.zahori.framework.exception.MethodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SSH {

    private static final Logger LOG = LoggerFactory.getLogger(SSH.class);
    public static final String ERROR_AL_EJECUTAR_EL_COMANDO_CAUSA = "Error al ejecutar el comando. Causa: ";

    protected final String server;
    protected final String user;
    protected final String password;
    protected final int port;

    private static final int DEFAULT_PORT = 22;

    public SSH(String server, String user, String password) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = DEFAULT_PORT;
    }

    public SSH(String server, String user, String password, int port) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = port;
    }

    public String executeCommand(String command) throws MethodException {

        LOG.debug("Executing command \"" + command + "\" on server \"" + server + "\"");
        long startTime = System.currentTimeMillis();

        String commandOutput = execute(command);

        LOG.debug("Output [time: " + (System.currentTimeMillis() - startTime) + " ms]: \n" + commandOutput + "\n");
        return commandOutput;
    }

    private String execute(String command) throws MethodException {

        ChannelExec channel = null;
        Session session = null;
        InputStream inputStream = null;
        InputStream errorStream = null;

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int exitCode = -1;

        try {

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");

            JSch jsch = new JSch();
            session = jsch.getSession(user, server, port);
            session.setPassword(password);
            session.setConfig(config);
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setOutputStream(null);
            channel.setErrStream(System.err);

            inputStream = channel.getInputStream();
            errorStream = channel.getErrStream();

            channel.connect();

            byte[] tmp = new byte[1024];
            byte[] tmpErr = new byte[1024];

            boolean connected = true;
            while (connected) {

                while (inputStream.available() > 0) {
                    int i = inputStream.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    // System.out.print(new String(tmp, 0, i));
                    out.append(new String(tmp, 0, i));
                }

                while (errorStream.available() > 0) {
                    int i = errorStream.read(tmpErr, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    // System.err.print(new String(tmpErr, 0, i));
                    err.append(new String(tmpErr, 0, i));
                }

                if (channel.isClosed()) {
                    exitCode = channel.getExitStatus();
                    connected = false;
                }

                Thread.sleep(1000L);
            }

            if (exitCode == 0) {
                return out.toString();
            } else {
                throw new MethodException(ERROR_AL_EJECUTAR_EL_COMANDO_CAUSA + err.toString());
            }

        } catch (Exception e) {
            // Example: java.net.ConnectException: Connection timed out: connect
            throw new MethodException(ERROR_AL_EJECUTAR_EL_COMANDO_CAUSA + e.getMessage());
        } finally {

            if ((channel != null) && channel.isConnected()) {
                channel.disconnect();
            }
            if ((session != null) && session.isConnected()) {
                session.disconnect();
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ioe) {
                    throw new MethodException(ERROR_AL_EJECUTAR_EL_COMANDO_CAUSA + ioe.getMessage());
                }
            }
            if (errorStream != null) {
                try {
                    errorStream.close();
                } catch (IOException ioe) {
                    throw new MethodException(ERROR_AL_EJECUTAR_EL_COMANDO_CAUSA + ioe.getMessage());
                }
            }
        }
    }

}
