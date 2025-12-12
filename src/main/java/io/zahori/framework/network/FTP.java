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
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE;

/**
 * FTP client for file transfers.
 * Extends AbstractRemoteClient for common functionality.
 */
public class FTP extends AbstractRemoteClient {

    private static final int DEFAULT_FTP_PORT = 21;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 20000;

    private int connectionTimeoutMillis;

    public FTP(String server, String user, String password) {
        super(server, user, password, DEFAULT_FTP_PORT);
        this.connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT;
    }

    public FTP(String server, String user, String password, int port) {
        super(server, user, password, port);
        this.connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT;
    }

    public FTP(String server, String user, String password, int port, int connectionTimeoutInSeconds) {
        super(server, user, password, port);
        this.connectionTimeoutMillis = connectionTimeoutInSeconds * 1000;
    }

    @Override
    protected int getDefaultPort() {
        return DEFAULT_FTP_PORT;
    }

    public boolean upload(String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {
        LOG.debug("Uploading local file \"{}\" to remote server ({}) directory \"{}/{}\"",
                buildPath(localDir, localFile), server, remoteDir, remoteFile);
        long startTime = System.currentTimeMillis();

        boolean result = load(TransferMode.UPLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File uploaded! [time: {} ms]", System.currentTimeMillis() - startTime);
        return result;
    }

    public boolean download(String remoteDir, String remoteFile, String localDir, String localFile)
            throws MethodException {
        LOG.debug("Downloading remote file ({}) \"{}/{}\" to local directory \"{}\"",
                server, remoteDir, remoteFile, buildPath(localDir, localFile));
        long startTime = System.currentTimeMillis();

        boolean result = load(TransferMode.DOWNLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File downloaded! [time: {} ms]", System.currentTimeMillis() - startTime);
        return result;
    }

    public Map<String, Date> getLastModifiedFileByPattern(String remoteDir, String remoteFilePatternName) {
        Map<String, Calendar> remoteFiles = new HashMap<>();
        Map<String, Date> result = new HashMap<>();

        try {
            FTPClient ftpClient = getFTPClient();
            FTPFile[] fileNames = ftpClient.listFiles(remoteDir);

            for (FTPFile currentFile : fileNames) {
                if (StringUtils.countMatches(currentFile.getName(), remoteFilePatternName) == 1) {
                    remoteFiles.put(currentFile.getName(), currentFile.getTimestamp());
                }
            }

            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (Exception e) {
            LOG.warn("Error getting last modified file: {}", e.getMessage());
            return result;
        }

        if (remoteFiles.keySet().isEmpty()) {
            return result;
        }

        String remoteFileName = "";
        for (String currentFileName : remoteFiles.keySet()) {
            if (remoteFileName.isEmpty()) {
                remoteFileName = currentFileName;
            } else {
                Calendar currentTimestamp = remoteFiles.get(remoteFileName);
                Calendar newTimestamp = remoteFiles.get(currentFileName);
                if (newTimestamp.compareTo(currentTimestamp) >= 0) {
                    remoteFileName = currentFileName;
                }
            }
        }

        result.put(remoteFileName, remoteFiles.get(remoteFileName).getTime());
        return result;
    }

    public boolean downloadLastModifiedFileByPattern(String remoteDir, String remoteFilePatternName, String localDir,
            String localFile) throws MethodException {
        Map<String, Date> remoteFile = getLastModifiedFileByPattern(remoteDir, remoteFilePatternName);
        if (remoteFile != null && !remoteFile.keySet().isEmpty()) {
            return load(TransferMode.DOWNLOAD, localDir, localFile, remoteDir,
                    String.valueOf(remoteFile.keySet().toArray()[0]));
        }
        return false;
    }

    public void setConnectionTimeout(int connectionTimeoutInSeconds) {
        connectionTimeoutMillis = connectionTimeoutInSeconds * 1000;
    }

    private FTPClient getFTPClient() throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.connect(server, port);
        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(BINARY_FILE_TYPE);
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.setConnectTimeout(connectionTimeoutMillis);

        return ftpClient;
    }

    private boolean load(TransferMode mode, String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {

        String local = buildPath(localDir, localFile);
        String remote = buildPath(remoteDir, remoteFile);

        boolean loaded = false;
        FTPClient ftpClient = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            ftpClient = getFTPClient();

            switch (mode) {
                case UPLOAD:
                    is = new FileInputStream(new File(local));
                    loaded = ftpClient.storeFile(remote, is);
                    break;
                case DOWNLOAD:
                    fos = new FileOutputStream(new File(local));
                    loaded = ftpClient.retrieveFile(remote, fos);
                    break;
                default:
                    break;
            }

            return loaded;

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
            if (ftpClient != null && ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (IOException ioe) {
                    LOG.warn("Error disconnecting FTP client: {}", ioe.getMessage());
                }
            }
            closeQuietly(fos, "FileOutputStream");
            closeQuietly(is, "InputStream");
        }
    }
}
