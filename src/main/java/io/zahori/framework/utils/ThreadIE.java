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

import io.zahori.framework.robot.UtilsRobot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Point;

import java.awt.*;
import java.util.List;

public class ThreadIE extends Thread {

    private static final Logger LOG = LogManager.getLogger(ThreadIE.class);

    private static final int MOVE_ZERO = 0;

    private UtilsRobot robot;

    private boolean execution = true;

    private List<Point> pointsList;

    private Point screenLimit;

    private boolean waiting;

    public ThreadIE(String name, List<Point> list, Point limit, boolean waitNext) {
        super(name);
        this.robot = new UtilsRobot();
        this.pointsList = list;
        this.screenLimit = limit;
        this.waiting = waitNext;
    }

    @Override
    public void run() {
        if (!this.pointsList.isEmpty()) {
            while (this.execution) {
                moveMousePointer();
            }
            while (this.waiting && !isExistingNextThread()) {
                shakeMousePointer();
            }
        }

        this.interrupt();
    }

    public void setExecutionFinish() {
        this.execution = false;
    }

    private void moveMousePointer() {
        try {
            int i = 0;
            while (i < pointsList.size()) {
                java.awt.Point actualPosition = MouseInfo.getPointerInfo().getLocation();
                this.robot.moveMousePointerAB((int) actualPosition.getX(), (int) actualPosition.getY(),
                        this.pointsList.get(i).getX(), this.pointsList.get(i).getY());
                Thread.sleep(2000L);
                i++;
            }
        } catch (InterruptedException e) {
            LOG.error("Error on moveMousePointer method (ThreadIE): " + e.getMessage());
            this.interrupt();
        }

    }

    private void shakeMousePointer() {
        try {
            java.awt.Point actualPosition = MouseInfo.getPointerInfo().getLocation();
            int averageX = (this.screenLimit.getX() - (this.screenLimit.getX() % 2)) / 2;
            int averageY = (this.screenLimit.getY() - (this.screenLimit.getY() % 2)) / 2;
            this.robot.moveMousePointerAB((int) actualPosition.getX(), (int) actualPosition.getY(), averageX,
                    MOVE_ZERO);
            Thread.sleep(1000L);
            this.robot.moveMousePointerAB(averageX, MOVE_ZERO, averageX, this.screenLimit.getY());
            this.robot.moveMousePointerAB(averageX, this.screenLimit.getY(), averageX, averageY);
            Thread.sleep(1000L);
            this.robot.moveMousePointerAB(averageX, averageY, MOVE_ZERO, averageY);
            this.robot.moveMousePointerAB(MOVE_ZERO, averageY, this.screenLimit.getX(), averageY);
            this.robot.moveMousePointerAB(this.screenLimit.getX(), averageY, averageX, averageY);
            Thread.sleep(1000L);
            this.robot.moveMousePointerAB(averageX, averageY, (int) actualPosition.getX(), (int) actualPosition.getY());
        } catch (InterruptedException e) {
            LOG.error("Error on shakeMousePointer method (ThreadIE): " + e.getMessage());
        }
    }

    private boolean isExistingNextThread() {
        ThreadGroup group = this.getThreadGroup().getParent();
        Thread[] threadsList = new Thread[group.activeCount()];
        int count = 0;
        int i = 0;
        while ((i < group.enumerate(threadsList)) && (count < 2)) {
            if (this.getClass().getSimpleName().equals(threadsList[i].getClass().getSimpleName())) {
                count++;
            }
            i++;
        }

        return (count >= 2);
    }
}
