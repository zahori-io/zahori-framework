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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.zahori.framework.exception.MethodException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.*;

public class SCP {

    private static final Logger LOG = LogManager.getLogger(SCP.class);
    public static final String ERROR_AL_SUBIR_DESCARGAR_EL_FICHERO_CAUSA = "Error al subir/descargar el fichero. Causa: ";

    protected final String server;
    protected final String user;
    protected final String password;
    protected final int port;

    private static final int DEFAULT_PORT = 22;

    private enum Mode {

        UPLOAD, DOWNLOAD
    }

    public SCP(String server, String user, String password) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = DEFAULT_PORT;
    }

    public SCP(String server, String user, String password, int port) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = port;
    }

    public void upload(String localDir, String localFile, String remoteDir, String remoteFile) throws MethodException {

        LOG.debug("Uploading local file \"" + localDir + "/" + localFile + "\" to remote server (" + server
                + ") directory \"" + remoteDir + "/" + remoteFile + "\"");
        long startTime = System.currentTimeMillis();

        load(Mode.UPLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File uploaded! [time: " + (System.currentTimeMillis() - startTime) + " ms]");
    }

    public void download(String remoteDir, String remoteFile, String localDir, String localFile)
            throws MethodException {

        LOG.debug("Downloading remote file (" + server + ") \"" + remoteDir + "/" + remoteFile
                + "\" to local directory \"" + localDir + "/" + localFile + "\"");
        long startTime = System.currentTimeMillis();

        load(Mode.DOWNLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File downloaded! [time: " + (System.currentTimeMillis() - startTime) + " ms]");
    }

    private void load(Mode mode, String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp;

        FileInputStream fis = null;
        BufferedReader br = null;
        BufferedWriter bw = null;

        String local = StringUtils.isBlank(localDir) ? localFile : localDir + "/" + localFile;

        try {

            JSch jsch = new JSch();
            session = jsch.getSession(user, server, port);
            session.setPassword(password);

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");

            session.setConfig(config);
            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(remoteDir);

            switch (mode) {
            case UPLOAD:
                File f = new File(local);
                fis = new FileInputStream(f);
                channelSftp.put(fis, remoteFile);
                break;
            case DOWNLOAD:
                br = new BufferedReader(new InputStreamReader(channelSftp.get(remoteFile)));
                String line;
                bw = new BufferedWriter(new PrintWriter(local));
                while ((line = br.readLine()) != null) {
                    bw.write(line);
                    bw.write(System.getProperty("line.separator"));
                }
                break;
            default:
            }

        } catch (Exception e) {
            switch (mode) {
            case UPLOAD:
                throw new MethodException("Error al subir el fichero. Causa: " + e.getMessage());
            case DOWNLOAD:
                throw new MethodException("Error al descargar el fichero. Causa: " + e.getMessage());
            default:
                throw new MethodException(ERROR_AL_SUBIR_DESCARGAR_EL_FICHERO_CAUSA + e.getMessage());
            }
        } finally {
            if ((channel != null) && channel.isConnected()) {
                channel.disconnect();
            }
            if ((session != null) && session.isConnected()) {
                session.disconnect();
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ioe) {
                    throw new MethodException(ERROR_AL_SUBIR_DESCARGAR_EL_FICHERO_CAUSA + ioe.getMessage());
                }
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    throw new MethodException(ERROR_AL_SUBIR_DESCARGAR_EL_FICHERO_CAUSA + ioe.getMessage());
                }
            }
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ioe) {
                    throw new MethodException(ERROR_AL_SUBIR_DESCARGAR_EL_FICHERO_CAUSA + ioe.getMessage());
                }
            }
        }
    }

}
