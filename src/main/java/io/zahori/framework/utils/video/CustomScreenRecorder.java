package io.zahori.framework.utils.video;

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

import java.awt.AWTException;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.monte.media.Format;
import org.monte.media.Registry;
import org.monte.screenrecorder.ScreenRecorder;

/**
 * The type Custom screen recorder.
 */
public class CustomScreenRecorder extends ScreenRecorder {

    private static final String TEMP_FILENAME_WITHOUT_EXTENSION = "currentRecording";

    private String currentTempExtension;

    private boolean areFilesDistributed = false;

    /**
     * Instantiates a new Custom screen recorder.
     *
     * @param cfg          the cfg
     * @param captureArea  the capture area
     * @param fileFormat   the file format
     * @param screenFormat the screen format
     * @param mouseFormat  the mouse format
     * @param audioFormat  the audio format
     * @param movieFolder  the movie folder
     * @throws IOException  the io exception
     * @throws AWTException the awt exception
     */
    public CustomScreenRecorder(GraphicsConfiguration cfg, Rectangle captureArea, Format fileFormat,
            Format screenFormat, Format mouseFormat, Format audioFormat, File movieFolder)
            throws IOException, AWTException {
        super(cfg, captureArea, fileFormat, screenFormat, mouseFormat, audioFormat, movieFolder);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.monte.screenrecorder.ScreenRecorder#createMovieFile(org.monte.media
     * .Format)
     */
    @Override
    protected File createMovieFile(Format fileFormat) throws IOException {
        this.currentTempExtension = Registry.getInstance().getExtension(fileFormat);
        final String tempFile = getTempFileName();

        final File fileToWriteMovie = new File(tempFile);
        if (fileToWriteMovie.exists()) {
            fileToWriteMovie.delete();
        }

        return fileToWriteMovie;
    }

    /**
     * Gets temp file name.
     *
     * @return the temp file name
     */
    public String getTempFileName() {
        return System.getProperty("java.io.tmpdir") + File.separator + TEMP_FILENAME_WITHOUT_EXTENSION + "."
                + this.currentTempExtension;
    }

    /**
     * Save as.
     *
     * @param filename the filename
     * @throws IOException the io exception
     */
    public void saveAs(String filename) throws IOException {
        this.stop();

        final File tempFile = this.getCreatedMovieFiles().get(0);

        final File destFile = getDestinationFile(filename);
        tempFile.renameTo(destFile);
    }

    private File getDestinationFile(String filename) {
        String destFolderSuffix = "";

        if (areFilesDistributed) {
            destFolderSuffix = File.separator + filename.charAt(filename.length() - 2)
                    + filename.charAt(filename.length() - 1);
        }

        final File file = new File(
                this.movieFolder + destFolderSuffix + File.separator + filename + "." + this.currentTempExtension);

        return file;
    }

    /**
     * Files should be distributed.
     *
     * @param areFilesDistributed the are files distributed
     */
    public void filesShouldBeDistributed(boolean areFilesDistributed) {
        this.areFilesDistributed = areFilesDistributed;
    }

    /**
     * Delete video temp boolean.
     *
     * @return the boolean
     */
    public boolean deleteVideoTemp() {
        boolean resultado = false;
        List<File> listaFicheros = getCreatedMovieFiles();

        for (File fichero : listaFicheros) {
            resultado = fichero.delete();
        }

        return resultado;
    }

}
