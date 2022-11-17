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

import org.monte.media.Format;
import org.monte.media.FormatKeys.*;
import org.monte.media.math.Rational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import static org.monte.media.FormatKeys.*;
import static org.monte.media.VideoFormatKeys.*;


public class VideoRecorder implements EnterpriseScreenRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(VideoRecorder.class);

    private CustomScreenRecorder screenRecorder;

    public VideoRecorder(File nameFolderVideos) throws IOException, AWTException {
        final File directorio = nameFolderVideos;

        if (!directorio.exists()) {
            throw new IOException("El path del directorio proporcionado no existe en el sistema.");
        }

        if (!directorio.isDirectory()) {
            throw new IOException("El path proporcionado no es un directorio.");
        }

        final Rectangle captureSize = new Rectangle(0, 0, Toolkit.getDefaultToolkit().getScreenSize().width,
                Toolkit.getDefaultToolkit().getScreenSize().height);

        final GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();

        this.screenRecorder = new CustomScreenRecorder(gc, captureSize,
                new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_AVI),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                        CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE, DepthKey, Integer.valueOf(24),
                        FrameRateKey, Rational.valueOf(15.0), QualityKey, Float.valueOf(1.0f), KeyFrameIntervalKey,
                        Integer.valueOf(15 * 60)),
                new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, "black", FrameRateKey, Rational.valueOf(30.0)),
                null, directorio);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.globalia.coreglobaliaauttest.video.EnterpriseScreenRecorder#start()
     */
    @Override
    public void start() {
        try {
            this.screenRecorder.start();
        } catch (final IOException e) {
            LOG.error("Error al inicializar la grabacion del video '" + this.getClass().getName() + "': "
                    + e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.globalia.coreglobaliaauttest.video.EnterpriseScreenRecorder#stop()
     */
    @Override
    public void stop() {
        try {
            this.screenRecorder.stop();
        } catch (final IOException e) {
            LOG.error(
                    "Error al finalizar la grabacion del video '" + this.getClass().getName() + "': " + e.getMessage());
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.globalia.coreglobaliaauttest.video.EnterpriseScreenRecorder#saveAs
     * (java.lang.String)
     */
    @Override
    public void saveAs(String fileName) throws IOException {
        try {
            this.screenRecorder.saveAs(fileName);
        } catch (final IOException e) {
            LOG.error("Error al guardar la grabacion del video '" + this.getClass().getName() + "': " + e.getMessage());
        }
    }

    @Override
    public void saveVideoFail(String name) throws IOException {
        saveAs(name);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.globalia.coreglobaliaauttest.video.EnterpriseScreenRecorder#
     * deleteVideoTemp()
     */
    @Override
    public boolean deleteVideoTemp() {
        return this.screenRecorder.deleteVideoTemp();
    }
}
