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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;

public class AndroidScreenRecorder implements EnterpriseScreenRecorder {

    private static final String TMP_FILENAME = "testVideo";
    private static final int DEFAULT_MAX_DURATION_MINUTES = 30;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int ANDROID_RECORD_LIMIT_MINUTES = 3;
    private static final String DOWNLOAD_FINISH_PATTERN = "1 file pulled";
    private static final String DOWNLOAD_ERROR_PATTERN = "does not exist";
    public static final String SCREENRECORD = "screenrecord";
    private int maxFiles;

    private String filePath;
    private Process recordProcess;
    String pathSeparator;

    public AndroidScreenRecorder(String filePath, Integer maxDurationMinutes) throws IOException {
        File directorio = new File(filePath);

        if (!directorio.exists()) {
            throw new IOException("El path del directorio proporcionado no existe en el sistema.");
        }

        if (!directorio.isDirectory()) {
            throw new IOException("El path proporcionado no es un directorio.");
        }

        this.filePath = filePath;
        pathSeparator = File.separator;

        if ((maxDurationMinutes == null) || (maxDurationMinutes.intValue() <= 0)) {
            maxFiles = (DEFAULT_MAX_DURATION_MINUTES / ANDROID_RECORD_LIMIT_MINUTES) + 1;
        } else {
            maxFiles = (maxDurationMinutes.intValue() / ANDROID_RECORD_LIMIT_MINUTES) + 1;
        }
    }

    @Override
    public void start() throws IOException {
        cleanTempVideoFilesOnDevice();
        StringBuilder command = new StringBuilder("adb shell \"");
        for (int i = 0; i < maxFiles; i++) {
            command.append("screenrecord --bit-rate 6000000 /sdcard/" + TMP_FILENAME + "_").append(i).append(".mp4");
            if (i < (maxFiles - 1)) {
                command.append(";");
            }
        }
        command.append("\"");
        recordProcess = Runtime.getRuntime().exec(command.toString(), null, new File(filePath));
    }

    @Override
    public void stop() throws IOException {
        int currentScreenRecordProcesses = getNumberofProcess(SCREENRECORD);
        recordProcess.destroy();
        boolean waitToStop = true;
        Chronometer chronoTimeout = new Chronometer();
        while (waitToStop && (chronoTimeout.getElapsedSeconds() < TIMEOUT_SECONDS)) {
            waitToStop = getNumberofProcess(SCREENRECORD) >= currentScreenRecordProcesses;
        }

        chronoTimeout = new Chronometer();
        while (recordProcess.isAlive() && (chronoTimeout.getElapsedSeconds() < TIMEOUT_SECONDS)) { }
    }

    private int getNumberofProcess(String command) throws IOException {
        Process countProcess = Runtime.getRuntime().exec("adb shell \"ps | grep " + command + "\"", null,
                new File(filePath));
        return StringUtils.countMatches(IOUtils.toString(countProcess.getInputStream(),"UTF-8"), SCREENRECORD);
    }

    @Override
    public void saveAs(String pathFile) throws IOException, FileNotFoundException {

        for (int i = 0; i < maxFiles; i++) {
            Process downloadProcess = Runtime.getRuntime().exec("adb pull /sdcard/" + TMP_FILENAME + "_" + i + ".mp4",
                    null, new File(filePath));
            String downloadProcessOutput = "";
            Chronometer chronoTimeout = new Chronometer();
            while ((chronoTimeout.getElapsedSeconds() < TIMEOUT_SECONDS) && (downloadProcessOutput.isEmpty()
                    || (!StringUtils.containsIgnoreCase(downloadProcessOutput, DOWNLOAD_FINISH_PATTERN)
                            && !StringUtils.containsIgnoreCase(downloadProcessOutput, DOWNLOAD_ERROR_PATTERN)))) {
                downloadProcessOutput = IOUtils.toString(downloadProcess.getInputStream(),"UTF-8");
            }
        }

        buildOutputFile(pathFile);

        this.deleteVideoTemp();
    }

    private void buildOutputFile(String fileName) throws IOException, FileNotFoundException {
        List<Track> videoTracks = new ArrayList<>();
        for (int i = 0; i < maxFiles; i++) {
            File videoFile = new File(filePath + pathSeparator + TMP_FILENAME + "_" + i + ".mp4");
            if (videoFile.exists()) {
                try {
                    Movie video = MovieCreator.build(filePath + pathSeparator + TMP_FILENAME + "_" + i + ".mp4");
                    videoTracks.addAll(video.getTracks());
                } catch (NullPointerException e) {
                    System.out.println("Video corrupto: " + filePath + pathSeparator + TMP_FILENAME + "_" + i + ".mp4");
                }

            }
        }

        if (!videoTracks.isEmpty()) {
            Movie video = new Movie();
            video.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
            Container out = new DefaultMp4Builder().build(video);
            FileOutputStream fos = new FileOutputStream(new File(filePath + pathSeparator + fileName + ".mp4"));
            out.writeContainer(fos.getChannel());
            fos.close();
        }

    }

    @Override
    public void saveVideoFail(String name) throws IOException {
        saveAs(name);
    }

    private void cleanTempVideoFilesOnDevice() throws IOException {
        for (int i = 0; i < maxFiles; i++) {
            Runtime.getRuntime().exec("adb shell \"rm /sdcard/" + TMP_FILENAME + "_" + i + ".mp4\"");
        }
    }

    @Override
    public boolean deleteVideoTemp() {
        try {
            for (int i = 0; i < maxFiles; i++) {
                Runtime.getRuntime().exec("adb shell \"rm /sdcard/" + TMP_FILENAME + "_" + i + ".mp4\"");
                Pause.shortPause();
                File videoFile = new File(filePath + pathSeparator + TMP_FILENAME + "_" + i + ".mp4");
                if (videoFile.exists()) {
                    videoFile.delete();
                }
            }
            return true;
        } catch (IOException e) {
            throw new ZahoriException(null, e.getMessage());
        }

    }
}
