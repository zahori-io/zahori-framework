package io.zahori.framework.utils;

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

import org.sikuli.basics.Settings;
import org.sikuli.script.Key;
import org.sikuli.script.KeyModifier;
import org.sikuli.script.Screen;

/**
 * The type Sikuli utils.
 */
public class SikuliUtils {

    private Screen screen;
    private int timeoutFindElement;

    /**
     * Instantiates a new Sikuli utils.
     *
     * @param screen             the screen
     * @param timeoutFindElement the timeout find element
     * @param readText           the read text
     */
    public SikuliUtils(Screen screen, int timeoutFindElement, boolean readText) {
        this.screen = screen;
        this.timeoutFindElement = timeoutFindElement;
        Settings.OcrTextRead = readText; // Region.text() function
        Settings.OcrTextSearch = readText; // finding text with
                                           // find("some text")
    }

    /**
     * Wait boolean.
     *
     * @param pathImage the path image
     * @return the boolean
     */
    public boolean wait(String pathImage) {
        return waitGeneric(pathImage, true);
    }

    /**
     * Wait dissappear boolean.
     *
     * @param pathImage the path image
     * @return the boolean
     */
    public boolean waitDissappear(String pathImage) {
        return waitGeneric(pathImage, false);
    }

    /**
     * Clear text.
     */
    public void clearText() {
        screen.type("a", KeyModifier.CTRL);
        screen.type(Key.BACKSPACE);
    }

    /**
     * Gets sikuli images path.
     *
     * @param resolution the resolution
     * @return the sikuli images path
     */
    public String getSikuliImagesPath(String resolution) {
        String basePath = "src/test/resources/sikuli_resources/";
        String path = basePath + resolution;
        File sikuliImagesDir = new File(path);
        if (sikuliImagesDir.exists()) {
            return path;
        } else {
            return basePath + "default";
        }
    }

    private boolean waitGeneric(String pathImage, boolean mustExist) {
        boolean condition = calculateCondition(pathImage, mustExist);
        Chronometer crono = new Chronometer();
        while ((crono.getElapsedSeconds() < timeoutFindElement) && !condition) {
            Pause.pause(1);
            condition = calculateCondition(pathImage, mustExist);
        }

        return calculateCondition(pathImage, mustExist);
    }

    private boolean calculateCondition(String pathImage, boolean mustExist) {
        if (mustExist) {
            return screen.exists(pathImage) != null;
        } else {
            return screen.exists(pathImage) == null;
        }
    }

}
