package io.zahori.framework.robot;

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
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zahori.framework.exception.MethodException;

public class UtilsRobot {

    private static final Logger LOG = LoggerFactory.getLogger(UtilsRobot.class);

    private Robot robot = null;

    private static final int MAX_SCROLL = 100;

    public UtilsRobot() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            LOG.debug("Error when trying construct Robot object.");
        }
    }

    public void moveMousePointer(int x, int y) {
        try {
            if (this.robot == null) {
                throw new MethodException("The Robot class object hasn't been setup right.");
            }

            this.robot.mouseMove(x, y);
        } catch (MethodException e) {
            LOG.error("Error when trying to move the mouse pointer: " + e.getMessage());
        }
    }

    public void shakeMousePointer(int x, int yOrigin, int yDestiny) {
        this.robot.mouseMove(x, yOrigin);
        for (int i = yOrigin; i <= yDestiny; i++) {
            this.robot.mouseMove(x, i);
            this.robot.delay(3);
        }
        // It returns to the origin point
        for (int i = yDestiny; i > yOrigin; i--) {
            this.robot.mouseMove(x, i);
            this.robot.delay(3);
        }
    }

    public void movePointerAutomatically(int x1, int y1, int x2, int y2) {
        this.robot.mouseMove(x1, y1);
        int i;

        // Vertical movement up-down
        for (i = y1; i <= y2; i++) {
            this.robot.mouseMove(x1, i);
            this.robot.delay(3);
        }

        // Horizontal movement left-right
        for (i = x1; i <= x2; i++) {
            this.robot.mouseMove(i, y2);
            this.robot.delay(3);
        }

        // Vertical movement down-up
        for (i = y2; i > y1; i--) {
            this.robot.mouseMove(x2, i);
            this.robot.delay(3);
        }

        // Horizontal movement right-left
        for (i = x2; i > x1; i--) {
            this.robot.mouseMove(i, y1);
            this.robot.delay(5);
        }
    }

    public void movePointerAutomaticallyVertical(int x1, int y1, int x2, int y2) throws InterruptedException {
        this.robot.mouseMove(x1, y1);
        int i;

        int divisor = x1;
        while (divisor < x2) {
            for (i = 0; i <= y2; i++) {
                this.robot.mouseMove(divisor, i);
                this.robot.delay(10);
            }
        }

    }

    public boolean scrollToElement(WebElement element) {
        int i = 0;
        while ((i < MAX_SCROLL) && (!element.isDisplayed())) {
            this.robot.mouseWheel(1);
            i++;
        }

        if (!element.isDisplayed()) {
            i = 0;
            while ((i < (2 * MAX_SCROLL)) && (!element.isDisplayed())) {
                this.robot.mouseWheel(-1);
                i++;
            }
        }

        return element.isDisplayed();
    }

    public boolean scrollElementDisplace(WebElement element, int times) {
        if (scrollToElement(element)) {
            this.robot.mouseWheel(times);
            return true;
        } else {
            return false;
        }
    }

    public void scroll(int times, int x, int y) {
        this.robot.mouseMove(x, y + 1);
        this.robot.mouseWheel(times);
    }

    public void scroll(int times) {
        Point posActual = MouseInfo.getPointerInfo().getLocation();
        this.robot.mouseMove((int) posActual.getX(), (int) posActual.getY());
        this.robot.mouseWheel(times);
    }

    public Point getMouseLocation() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        return p;
    }

    public void moveMousePointerAB(int x1, int y1, int x2, int y2) {

        // First it moves in horizontal axis (X).
        if (x1 < x2) {
            for (int i = x1; i <= x2; i++) {
                this.robot.mouseMove(i, y1);
                this.robot.delay(1);
            }
        } else if (x2 < x1) {
            for (int i = x1; i > x2; i--) {
                this.robot.mouseMove(i, y1);
                this.robot.delay(1);
            }
        }

        // Then, it moves in vertical axis (Y).
        if (y1 < y2) {
            for (int i = y1; i <= y2; i++) {
                this.robot.mouseMove(x2, i);
                this.robot.delay(1);
            }
        } else if (y2 < y1) {
            for (int i = y1; i > y2; i--) {
                this.robot.mouseMove(x2, i);
                this.robot.delay(1);
            }
        }
    }

    public void click() {
        this.robot.mousePress(InputEvent.BUTTON1_MASK);
        this.robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }

    public void pressKey(int key) {
        this.robot.keyPress(key);
        this.robot.keyRelease(key);
    }

    public void takeScreenshot(String fileName) {
        try {
            Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRectangle = new Rectangle(screenSize);
            BufferedImage image = this.robot.createScreenCapture(screenRectangle);
            ImageIO.write(image, "png", new File(fileName));
        } catch (IOException e) {
            LOG.error("IO Error on method takeScreenshot: " + e.getMessage());
        }
    }

}
