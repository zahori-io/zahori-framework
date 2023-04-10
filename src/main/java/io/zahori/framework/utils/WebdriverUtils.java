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

import io.zahori.framework.driver.browserfactory.WebDriverBrowserSelenium;
import io.zahori.framework.exception.WebdriverException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


public class WebdriverUtils {

    private static final Logger LOG = LogManager.getLogger(WebdriverUtils.class);
    public static final String HTML = "//html";
    public static final String HTML_OBJECT_HASN_T_BEEN_FOUND = "HTML object hasn't been found.";

    private WebdriverUtils() {
    }

    public static int getBrowserBarHeight(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.xpath(HTML));
            if (element == null) {
                throw new WebdriverException("HTML object cannot be found.");
            }
            int htmlY = element.getSize().getHeight();
            int windowY = driver.manage().window().getSize().height + driver.manage().window().getPosition().getY();
            int verticalCorrector = windowY - htmlY;

            return verticalCorrector;
        } catch (WebdriverException e) {
            LOG.debug("Error on getBrowserBarHeight method: " + e.getMessage());
            return 0;
        }

    }

    public static Point getElementCenter(WebDriver driver, WebElement element) {
        try {
            if (element == null) {
                throw new WebdriverException("The referenced web element isn't visible or it doesn't exist.");
            }
            int verticalCorrector = getBrowserBarHeight(driver);

            Point location = element.getLocation();
            int destinyX = location.getX() + ((element.getSize().getWidth() - (element.getSize().getWidth() % 2)) / 2);
            int destinyY = location.getY() + ((element.getSize().getHeight() - (element.getSize().getHeight() % 2)) / 2)
                    + verticalCorrector;

            return new Point(destinyX, destinyY);

        } catch (WebdriverException e) {
            LOG.debug("Error on getElementCenter method: " + e.getMessage());
            return null;
        }
    }

    public static int getYScreen(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.xpath(HTML));
            if (element == null) {
                throw new WebdriverException(HTML_OBJECT_HASN_T_BEEN_FOUND);
            }
            int verticalCorrector = getBrowserBarHeight(driver);
            return verticalCorrector + element.getSize().getHeight();
        } catch (WebdriverException e) {
            LOG.error("Error on getYScreen method: " + e.getMessage());
            return 0;
        }

    }

    public static int getXScreen(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.xpath(HTML));
            if (element == null) {
                throw new WebdriverException(HTML_OBJECT_HASN_T_BEEN_FOUND);
            }
            return element.getSize().getWidth();
        } catch (WebdriverException e) {
            LOG.error("Error on getXScreen method: " + e.getMessage());
            return 0;
        }

    }

    public static Point getScreenLimit(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.xpath("//body"));
            if (element == null) {
                throw new WebdriverException(HTML_OBJECT_HASN_T_BEEN_FOUND);
            }
            int verticalCorrector = getBrowserBarHeight(driver);
            return new Point(element.getSize().getWidth(), verticalCorrector + element.getSize().getHeight());

        } catch (WebdriverException e) {
            LOG.error("Error on getScreenLimite method: " + e.getMessage());
            return new Point(0, 0);
        }

    }
}
