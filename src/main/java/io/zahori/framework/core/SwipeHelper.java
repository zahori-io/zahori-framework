package io.zahori.framework.core;

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

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.Collections;

/**
 * Helper class for swipe gestures on mobile devices.
 * Centralizes swipe logic to avoid code duplication between Page and PageElement.
 */
public final class SwipeHelper {

    private static final Duration MOVE_DURATION = Duration.ofMillis(500);

    private SwipeHelper() {
        // Utility class - no instantiation
    }

    /**
     * Performs a vertical swipe gesture.
     *
     * @param driver the WebDriver (must be AppiumDriver)
     * @param x      the x coordinate
     * @param startY the starting y coordinate
     * @param endY   the ending y coordinate
     * @param caller description for error messages
     */
    public static void swipeVertical(WebDriver driver, int x, int startY, int endY, String caller) {
        performSwipe(driver, x, startY, x, endY, caller);
    }

    /**
     * Performs a horizontal swipe gesture.
     *
     * @param driver the WebDriver (must be AppiumDriver)
     * @param y      the y coordinate
     * @param startX the starting x coordinate
     * @param endX   the ending x coordinate
     * @param caller description for error messages
     */
    public static void swipeHorizontal(WebDriver driver, int y, int startX, int endX, String caller) {
        performSwipe(driver, startX, y, endX, y, caller);
    }

    /**
     * Core swipe implementation using Selenium 4 W3C Actions.
     */
    private static void performSwipe(WebDriver driver, int startX, int startY, int endX, int endY, String caller) {
        try {
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seqSwipe = new Sequence(finger, 1);

            seqSwipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, startY));
            seqSwipe.addAction(finger.createPointerDown(0));
            seqSwipe.addAction(finger.createPointerMove(MOVE_DURATION, PointerInput.Origin.viewport(), endX, endY));
            seqSwipe.addAction(finger.createPointerUp(0));

            ((AppiumDriver) driver).perform(Collections.singletonList(seqSwipe));
        } catch (Exception e) {
            throw new RuntimeException("Unable to swipe: " + caller + " - " + e.getMessage(), e);
        }
    }
}
