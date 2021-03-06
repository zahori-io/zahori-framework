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
import io.appium.java_client.MobileElement;
import io.appium.java_client.windows.PressesKeyCode;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.touch.TouchActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The type Mobile page element.
 */
public class MobilePageElement extends PageElement {

    private AppiumDriver appiumDriver;

    /**
     * Instantiates a new Mobile page element.
     *
     * @param page    the page
     * @param name    the name
     * @param locator the locator
     */
    public MobilePageElement(Page page, String name, Locator locator) {
        super(page, name, locator);
        appiumDriver = (AppiumDriver) page.testContext.driver;
    }

    /**
     * Write and hide keyboard.
     *
     * @param text the text
     */
    public void writeAndHideKeyboard(String text) {
        WebElement webElement = findElement();
        testContext.logInfo("Write text \"" + text + "\" on " + this);
        webElement.sendKeys(text);
        hideKeyboard();
    }

    /**
     * Tap.
     *
     * @param fingers  the fingers
     * @param duration the duration
     */
    public void tap(int fingers, int duration) {
        MobileElement mobileElement = (MobileElement) findElement();
        TouchActions action = new TouchActions(appiumDriver);
        action.singleTap(mobileElement);
        action.perform();
    }

    @Override
    public void selectOptionIndex(int option) {
        WebElement select = findElement();
        select.click();

        testContext.logInfo("Select option '" + option + "' " + this);

        String currentContext = appiumDriver.getContext();
        // Cambiamos al contexto nativo si no es una aplicación nativa
        if (!"NATIVE_APP".equals(currentContext)) {
            appiumDriver.context("NATIVE_APP");
        }

        // Android
        if (testContext.isAndroidDriver()) {
            List<WebElement> options = appiumDriver.findElements(By.className("android.widget.CheckedTextView"));
            if (options.size() >= option) {
                WebElement opcion = options.get(option - 1);
                opcion.click();
            } else {
                throw new RuntimeException("Select option not found: " + this);
            }
        }

        // iOS
        if (testContext.isIOSDriver()) {
            // TODO
            testContext.logInfo(appiumDriver.getPageSource());
        }

        // Volvemos a cambiar al contexto original
        if (!"NATIVE_APP".equals(currentContext)) {
            appiumDriver.context(currentContext);
        }
    }

    /**
     * Select date in native calendar.
     *
     * @param date the date
     */
    public void selectDateInNativeCalendar(String date) {

        if (StringUtils.isEmpty(date)) {
            return;
        }

        String currentContext = appiumDriver.getContext();

        // Cambiamos al contexto nativo si no es una aplicación nativa
        if (!"NATIVE_APP".equals(currentContext)) {
            appiumDriver.context("NATIVE_APP");
        }

        // Android
        if (testContext.isAndroidDriver()) {

            // Date. Example: "10 marzo 2016"
            WebElement fechaOrigen = appiumDriver.findElement(By.xpath("//*[@content-desc='" + date + "']"));
            fechaOrigen.click();

            // Accept button
            WebElement botonAceptar = appiumDriver.findElement(By.xpath("//*[@resource-id='android:id/button1']"));
            botonAceptar.click();

            testContext.logInfo("Selected date '" + date + "' in Android native calendar");
        }

        // iOS
        if (testContext.isIOSDriver()) {
            // TODO
            testContext.logInfo("selectDateInNativeCalendar NOT IMPLEMENTED YET");
        }

        // Volvemos a cambiar al contexto original
        if (!"NATIVE_APP".equals(currentContext)) {
            appiumDriver.context(currentContext);
        }
    }

    /**
     * Hide keyboard.
     */
    public void hideKeyboard() {
        testContext.logInfo("Hide keyboard");
        appiumDriver.hideKeyboard();
    }

    /**
     * Send keys.
     *
     * @param string the string
     */
    public void sendKeys(String string) {
        if (StringUtils.isBlank(string)) {
            return;
        }

        if (testContext.isAndroidDriver()) {
            for (int i = 0; i < string.length(); i++) {
                Integer charCode = getCharCode(string.charAt(i));
                if (charCode != null) {
                    ((PressesKeyCode) appiumDriver).pressKeyCode(charCode.intValue());
                } else {
                    testContext.logInfo("Couldn't find the code for character '" + string.charAt(i) + "'");
                }
            }
            return;
        }

        if (testContext.isIOSDriver()) {
            // TODO?
            return;
        }
    }

    private Integer getCharCode(char character) {
        /*
         * http://developer.android.com/reference/android/view/KeyEvent.html#
         * KEYCODE_AT Se pueden simular con adb: adb shell input keyevent <CODE>
         *
         * 3 HOME BUTTON 4 BACK BUTTON 5 parace que vuelve a poner el foco en el
         * mismo elemento 6 apaga la pantalla "0" - "9"-> 7 - 16 17 * 18 19 # 20
         * cursor abajo (o arriba?) 21 cursor izq 22 cursor dcha 23 cursor
         * arriba (o abajo?) 23 SELECT (MIDDLE) BUTTON 24 volumen 25 volumen 26
         * pantalla 27 28 a - z-> 29 - 54 55 , 56 . SHIFT - 59 SPACE - 62 ENTER
         * - 66 67 BACKSPACE | DEL 68 ` 69 - 70 = 71 [ 72 ] 73 \ 74; 75 ' 76 /
         * 77 @ 78 79 falla el test 80 81 + 82 MENU BUTTON 85 86 abre app
         * musica? 87 reproducir musica 97 creo que cierra el teclado 117 118
         * pulsación larga en botón home 120 captura pantalla 126 reproducir
         * musica 146 pasa al siguiente campo de texto (INTRO ?) 154 / 155 * 156
         * - 157 + 159, 161 = 162 ( 163 ) 176 SETTINGS 187 aplicaciones abiertas
         * (pulsación larga botón izquierdo?) 207 abre aplicacion contactos 208
         * calendario 209 aplicacion musica 210 calculadora 219 pulsación larga
         * en botón home 220 brillo - 221 brillo + 231 abre app de google para
         * busquedas
         */
        /*
         * import io.appium.java_client.android.AndroidKeyCode;
         * driver.sendKeyEvent(AndroidKeyCode.BACK);
         * driver.sendKeyEvent(AndroidKeyCode.BACKSPACE);
         * driver.sendKeyEvent(AndroidKeyCode.DEL);
         * driver.sendKeyEvent(AndroidKeyCode.ENTER);
         * driver.sendKeyEvent(AndroidKeyCode.HOME);
         * driver.sendKeyEvent(AndroidKeyCode.SETTINGS);
         * driver.sendKeyEvent(AndroidKeyCode.SPACE);
         */

        Map<String, Integer> codes = new HashMap<>();
        codes.put("0", Integer.valueOf(7));
        codes.put("1", Integer.valueOf(8));
        codes.put("2", Integer.valueOf(9));
        codes.put("3", Integer.valueOf(10));
        codes.put("4", Integer.valueOf(11));
        codes.put("5", Integer.valueOf(12));
        codes.put("6", Integer.valueOf(13));
        codes.put("7", Integer.valueOf(14));
        codes.put("8", Integer.valueOf(15));
        codes.put("9", Integer.valueOf(16));
        codes.put("*", Integer.valueOf(17));
        codes.put("#", Integer.valueOf(19));
        codes.put("a", Integer.valueOf(29));
        codes.put("b", Integer.valueOf(30));
        codes.put("c", Integer.valueOf(31));
        codes.put("d", Integer.valueOf(32));
        codes.put("e", Integer.valueOf(33));
        codes.put("f", Integer.valueOf(34));
        codes.put("g", Integer.valueOf(35));
        codes.put("h", Integer.valueOf(36));
        codes.put("i", Integer.valueOf(37));
        codes.put("j", Integer.valueOf(38));
        codes.put("k", Integer.valueOf(39));
        codes.put("l", Integer.valueOf(40));
        codes.put("m", Integer.valueOf(41));
        codes.put("n", Integer.valueOf(42));
        codes.put("o", Integer.valueOf(43));
        codes.put("p", Integer.valueOf(44));
        codes.put("q", Integer.valueOf(45));
        codes.put("r", Integer.valueOf(46));
        codes.put("s", Integer.valueOf(47));
        codes.put("t", Integer.valueOf(48));
        codes.put("u", Integer.valueOf(49));
        codes.put("v", Integer.valueOf(50));
        codes.put("w", Integer.valueOf(51));
        codes.put("x", Integer.valueOf(52));
        codes.put("y", Integer.valueOf(53));
        codes.put("z", Integer.valueOf(54));
        codes.put(",", Integer.valueOf(55));
        codes.put(".", Integer.valueOf(56));
        codes.put(" ", Integer.valueOf(62));

        return codes.get(String.valueOf(character));
    }
}
