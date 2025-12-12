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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SCP/SFTP client for secure file transfers.
 * Extends AbstractRemoteClient for common functionality.
 */
public class SCP extends AbstractRemoteClient {

    private static final int DEFAULT_SCP_PORT = 22;

    public SCP(String server, String user, String password) {
        super(server, user, password, DEFAULT_SCP_PORT);
    }

    public SCP(String server, String user, String password, int port) {
        super(server, user, password, port);
    }

    @Override
    protected int getDefaultPort() {
        return DEFAULT_SCP_PORT;
    }

    public void upload(String localDir, String localFile, String remoteDir, String remoteFile) throws MethodException {
        LOG.debug("Uploading local file \"{}\" to remote server ({}) directory \"{}/{}\"",
                buildPath(localDir, localFile), server, remoteDir, remoteFile);

        executeWithTiming("File upload", () ->
            load(TransferMode.UPLOAD, localDir, localFile, remoteDir, remoteFile));
    }

    public void download(String remoteDir, String remoteFile, String localDir, String localFile)
            throws MethodException {
        LOG.debug("Downloading remote file ({}) \"{}/{}\" to local directory \"{}\"",
                server, remoteDir, remoteFile, buildPath(localDir, localFile));

        executeWithTiming("File download", () ->
            load(TransferMode.DOWNLOAD, localDir, localFile, remoteDir, remoteFile));
    }

    private void load(TransferMode mode, String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp;

        FileInputStream fis = null;
        BufferedReader br = null;
        BufferedWriter bw = null;

        String local = buildPath(localDir, localFile);

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
                    br = new BufferedReader(new InputStreamReader(channelSftp.get(remoteFile), StandardCharsets.UTF_8));
                    String line;
                    bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(local), StandardCharsets.UTF_8));
                    while ((line = br.readLine()) != null) {
                        bw.write(line);
                        bw.write(System.getProperty("line.separator"));
                    }
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            switch (mode) {
                case UPLOAD:
                    throw new MethodException(ERROR_UPLOAD + e.getMessage());
                case DOWNLOAD:
                    throw new MethodException(ERROR_DOWNLOAD + e.getMessage());
                default:
                    throw new MethodException(ERROR_TRANSFER + e.getMessage());
            }
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            closeQuietly(fis, "FileInputStream");
            closeQuietly(br, "BufferedReader");
            closeQuietly(bw, "BufferedWriter");
        }
    }
}
