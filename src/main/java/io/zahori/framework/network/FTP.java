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

import static org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zahori.framework.exception.MethodException;

/**
 * The type Ftp.
 */
public class FTP {

    private static final Logger LOG = LoggerFactory.getLogger(FTP.class);

    /**
     * The Server.
     */
    protected final String server;
    /**
     * The User.
     */
    protected final String user;
    /**
     * The Password.
     */
    protected final String password;
    /**
     * The Port.
     */
    protected final int port;
    /**
     * The Connection timeout seconds.
     */
    protected int connectionTimeoutSeconds;

    private static final int DEFAULT_PORT = 21;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 20000;

    private enum Mode {

        /**
         * Upload mode.
         */
        UPLOAD,
        /**
         * Download mode.
         */
        DOWNLOAD
    }

    /**
     * Instantiates a new Ftp.
     *
     * @param server   the server
     * @param user     the user
     * @param password the password
     */
    public FTP(String server, String user, String password) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = DEFAULT_PORT;
        this.connectionTimeoutSeconds = DEFAULT_CONNECTION_TIMEOUT;
    }

    /**
     * Instantiates a new Ftp.
     *
     * @param server   the server
     * @param user     the user
     * @param password the password
     * @param port     the port
     */
    public FTP(String server, String user, String password, int port) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = port;
        this.connectionTimeoutSeconds = DEFAULT_CONNECTION_TIMEOUT;
    }

    /**
     * Instantiates a new Ftp.
     *
     * @param server                     the server
     * @param user                       the user
     * @param password                   the password
     * @param port                       the port
     * @param connectionTimeoutInSeconds the connection timeout in seconds
     */
    public FTP(String server, String user, String password, int port, int connectionTimeoutInSeconds) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.port = port;
        this.connectionTimeoutSeconds = connectionTimeoutInSeconds * 1000;
    }

    /**
     * Upload boolean.
     *
     * @param localDir   the local dir
     * @param localFile  the local file
     * @param remoteDir  the remote dir
     * @param remoteFile the remote file
     * @return the boolean
     * @throws MethodException the method exception
     */
    public boolean upload(String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {

        LOG.debug("Uploading local file \"" + localDir + "/" + localFile + "\" to remote server (" + server
                + ") directory \"" + remoteDir + "/" + remoteFile + "\"");
        long startTime = System.currentTimeMillis();

        boolean result = load(Mode.UPLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File uploaded! [time: " + (System.currentTimeMillis() - startTime) + " ms]");
        return result;
    }

    /**
     * Download boolean.
     *
     * @param remoteDir  the remote dir
     * @param remoteFile the remote file
     * @param localDir   the local dir
     * @param localFile  the local file
     * @return the boolean
     * @throws MethodException the method exception
     */
    public boolean download(String remoteDir, String remoteFile, String localDir, String localFile)
            throws MethodException {

        LOG.debug("Downloading remote file (" + server + ") \"" + remoteDir + "/" + remoteFile
                + "\" to local directory \"" + localDir + "/" + localFile + "\"");
        long startTime = System.currentTimeMillis();

        boolean result = load(Mode.DOWNLOAD, localDir, localFile, remoteDir, remoteFile);

        LOG.debug("File downloaded! [time: " + (System.currentTimeMillis() - startTime) + " ms]");
        return result;
    }

    /**
     * Gets last modified file by pattern.
     *
     * @param remoteDir             the remote dir
     * @param remoteFilePatternName the remote file pattern name
     * @return the last modified file by pattern
     */
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
            return result;
        }

        if (remoteFiles.keySet().isEmpty()) {
            return result;
        } else {
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
    }

    /**
     * Download last modified file by pattern boolean.
     *
     * @param remoteDir             the remote dir
     * @param remoteFilePatternName the remote file pattern name
     * @param localDir              the local dir
     * @param localFile             the local file
     * @return the boolean
     * @throws MethodException the method exception
     */
    public boolean downloadLastModifiedFileByPattern(String remoteDir, String remoteFilePatternName, String localDir,
            String localFile) throws MethodException {
        Map<String, Date> remoteFile = getLastModifiedFileByPattern(remoteDir, remoteFilePatternName);
        if ((remoteFile != null) && !remoteFile.keySet().isEmpty()) {
            return load(Mode.DOWNLOAD, localDir, localFile, remoteDir,
                    String.valueOf(remoteFile.keySet().toArray()[0]));
        } else {
            return false;
        }
    }

    /**
     * Sets connection timeout.
     *
     * @param connectionTimeoutInSeconds the connection timeout in seconds
     */
    public void setConnectionTimeout(int connectionTimeoutInSeconds) {
        connectionTimeoutSeconds = connectionTimeoutInSeconds * 1000;
    }

    private FTPClient getFTPClient() throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.connect(server, port);
        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(BINARY_FILE_TYPE);
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.setConnectTimeout(connectionTimeoutSeconds);

        return ftpClient;
    }

    private boolean load(Mode mode, String localDir, String localFile, String remoteDir, String remoteFile)
            throws MethodException {

        String local = StringUtils.isBlank(localDir) ? localFile : localDir + "/" + localFile;
        String remote = StringUtils.isBlank(remoteDir) ? remoteFile : remoteDir + "/" + remoteFile;

        boolean loaded = false;
        FTPClient ftpClient = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            ftpClient = getFTPClient();

            switch (mode) {
            case UPLOAD:
                // Upload file (default remote directory is: user's home
                // directory)
                is = new FileInputStream(new File(local));
                loaded = ftpClient.storeFile(remote, is);
                break;
            case DOWNLOAD:
                // Download file
                fos = new FileOutputStream(new File(local));
                loaded = ftpClient.retrieveFile(remote, fos);
                break;
            default:
            }

            return loaded;

        } catch (Exception e) {
            switch (mode) {
            case UPLOAD:
                throw new MethodException("Error al subir el fichero. Causa: " + e.getMessage());
            case DOWNLOAD:
                throw new MethodException("Error al descargar el fichero. Causa: " + e.getMessage());
            default:
                throw new MethodException("Error al subir/descargar el fichero. Causa: " + e.getMessage());
            }
        } finally {

            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (IOException ioe) {
                    throw new MethodException("Error al subir/descargar el fichero. Causa: " + ioe.getMessage());
                }
            }

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ioe) {
                    throw new MethodException("Error al subir/descargar el fichero. Causa: " + ioe.getMessage());
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    throw new MethodException("Error al subir/descargar el fichero. Causa: " + ioe.getMessage());
                }
            }
        }
    }

}
