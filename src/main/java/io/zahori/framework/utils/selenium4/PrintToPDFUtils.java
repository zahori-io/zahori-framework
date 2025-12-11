package io.zahori.framework.utils.selenium4;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.print.PageMargin;
import org.openqa.selenium.print.PageSize;
import org.openqa.selenium.print.PrintOptions;

/**
 * Utilidades para imprimir paginas a PDF (Selenium 4).
 *
 * La interface PrintsPage de Selenium 4 permite generar PDFs directamente
 * desde el navegador, sin necesidad de capturas de pantalla.
 *
 * Navegadores soportados:
 * - Chrome/Chromium (headless o no)
 * - Edge
 * - Firefox
 *
 * Ejemplo de uso:
 * <pre>
 * // PDF con configuracion por defecto
 * PrintToPDFUtils.printToPDF(driver, "output.pdf");
 *
 * // PDF con opciones personalizadas
 * PrintOptions options = PrintToPDFUtils.createA4LandscapeOptions();
 * PrintToPDFUtils.printToPDF(driver, "report.pdf", options);
 *
 * // Obtener PDF como Base64
 * String base64Pdf = PrintToPDFUtils.printToBase64(driver);
 * </pre>
 */
public final class PrintToPDFUtils {

    private static final Logger LOG = LogManager.getLogger(PrintToPDFUtils.class);

    private PrintToPDFUtils() {
        // Utility class
    }

    /**
     * Tamanos de pagina predefinidos.
     */
    public enum PageFormat {
        /** A4: 21.0 x 29.7 cm */
        A4(21.0, 29.7),
        /** Letter US: 21.59 x 27.94 cm */
        LETTER(21.59, 27.94),
        /** Legal US: 21.59 x 35.56 cm */
        LEGAL(21.59, 35.56),
        /** A3: 29.7 x 42.0 cm */
        A3(29.7, 42.0),
        /** Tabloid: 27.94 x 43.18 cm */
        TABLOID(27.94, 43.18);

        final double widthCm;
        final double heightCm;

        PageFormat(double widthCm, double heightCm) {
            this.widthCm = widthCm;
            this.heightCm = heightCm;
        }
    }

    /**
     * Imprime la pagina actual a PDF con opciones por defecto (A4, portrait).
     *
     * @param driver WebDriver (Chrome, Edge o Firefox)
     * @param outputPath ruta del archivo PDF de salida
     * @return Path del archivo generado
     */
    public static Path printToPDF(WebDriver driver, String outputPath) {
        return printToPDF(driver, outputPath, createDefaultOptions());
    }

    /**
     * Imprime la pagina actual a PDF con opciones personalizadas.
     *
     * @param driver WebDriver (Chrome, Edge o Firefox)
     * @param outputPath ruta del archivo PDF de salida
     * @param options opciones de impresion
     * @return Path del archivo generado
     */
    public static Path printToPDF(WebDriver driver, String outputPath, PrintOptions options) {
        if (!supportsPrint(driver)) {
            throw new UnsupportedOperationException("El driver no soporta PrintsPage: " + driver.getClass().getSimpleName());
        }

        try {
            PrintsPage printsPage = (PrintsPage) driver;
            Pdf pdf = printsPage.print(options);

            byte[] pdfBytes = Base64.getDecoder().decode(pdf.getContent());
            Path path = Path.of(outputPath);

            // Crear directorios padres si no existen
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.write(path, pdfBytes);
            LOG.info("PDF generado: {} ({} bytes)", outputPath, pdfBytes.length);
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Error escribiendo PDF: " + outputPath, e);
        }
    }

    /**
     * Obtiene la pagina actual como PDF en Base64.
     *
     * @param driver WebDriver
     * @return String Base64 del PDF
     */
    public static String printToBase64(WebDriver driver) {
        return printToBase64(driver, createDefaultOptions());
    }

    /**
     * Obtiene la pagina actual como PDF en Base64 con opciones.
     *
     * @param driver WebDriver
     * @param options opciones de impresion
     * @return String Base64 del PDF
     */
    public static String printToBase64(WebDriver driver, PrintOptions options) {
        if (!supportsPrint(driver)) {
            throw new UnsupportedOperationException("El driver no soporta PrintsPage");
        }

        PrintsPage printsPage = (PrintsPage) driver;
        Pdf pdf = printsPage.print(options);
        LOG.debug("PDF Base64 generado");
        return pdf.getContent();
    }

    /**
     * Obtiene la pagina actual como bytes PDF.
     *
     * @param driver WebDriver
     * @return bytes del PDF
     */
    public static byte[] printToBytes(WebDriver driver) {
        String base64 = printToBase64(driver);
        return Base64.getDecoder().decode(base64);
    }

    // ==================== Factory Methods para PrintOptions ====================

    /**
     * Crea opciones por defecto: A4, portrait, margenes normales.
     */
    public static PrintOptions createDefaultOptions() {
        PrintOptions options = new PrintOptions();
        options.setPageSize(new PageSize(PageFormat.A4.widthCm, PageFormat.A4.heightCm));
        options.setOrientation(PrintOptions.Orientation.PORTRAIT);
        options.setPageMargin(new PageMargin(1.0, 1.0, 1.0, 1.0)); // 1cm margenes
        return options;
    }

    /**
     * Crea opciones A4 horizontal (landscape).
     */
    public static PrintOptions createA4LandscapeOptions() {
        PrintOptions options = createDefaultOptions();
        options.setOrientation(PrintOptions.Orientation.LANDSCAPE);
        return options;
    }

    /**
     * Crea opciones sin margenes (full page).
     */
    public static PrintOptions createFullPageOptions() {
        PrintOptions options = createDefaultOptions();
        options.setPageMargin(new PageMargin(0, 0, 0, 0));
        return options;
    }

    /**
     * Crea opciones para un formato de pagina especifico.
     *
     * @param format formato de pagina
     * @param landscape true para horizontal, false para vertical
     * @return PrintOptions configuradas
     */
    public static PrintOptions createOptions(PageFormat format, boolean landscape) {
        PrintOptions options = new PrintOptions();
        options.setPageSize(new PageSize(format.widthCm, format.heightCm));
        options.setOrientation(landscape ? PrintOptions.Orientation.LANDSCAPE : PrintOptions.Orientation.PORTRAIT);
        options.setPageMargin(new PageMargin(1.0, 1.0, 1.0, 1.0));
        return options;
    }

    /**
     * Crea opciones con rango de paginas especifico.
     *
     * @param pageRanges rangos de paginas (ej: "1-3", "1,3,5", "2-")
     * @return PrintOptions configuradas
     */
    public static PrintOptions createOptionsWithPageRanges(String pageRanges) {
        PrintOptions options = createDefaultOptions();
        options.setPageRanges(pageRanges);
        return options;
    }

    /**
     * Crea opciones con escala personalizada.
     *
     * @param scale factor de escala (0.1 a 2.0)
     * @return PrintOptions configuradas
     */
    public static PrintOptions createOptionsWithScale(double scale) {
        if (scale < 0.1 || scale > 2.0) {
            throw new IllegalArgumentException("Scale debe estar entre 0.1 y 2.0");
        }
        PrintOptions options = createDefaultOptions();
        options.setScale(scale);
        return options;
    }

    /**
     * Crea opciones incluyendo fondo de pagina (background graphics).
     */
    public static PrintOptions createOptionsWithBackground() {
        PrintOptions options = createDefaultOptions();
        options.setBackground(true);
        return options;
    }

    // ==================== Utility Methods ====================

    /**
     * Verifica si el driver soporta impresion a PDF.
     *
     * @param driver WebDriver a verificar
     * @return true si soporta PrintsPage
     */
    public static boolean supportsPrint(WebDriver driver) {
        return driver instanceof PrintsPage;
    }
}
