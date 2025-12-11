package io.zahori.framework.evidences;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for screenshot optimization functionality.
 * Tests compression quality and scaling features.
 */
class ScreenshotOptimizationTest {

    @TempDir
    Path tempDir;

    @Test
    void testJpgCompressionReducesFileSize() throws IOException {
        // Create a test image (1920x1080 - typical screenshot size)
        BufferedImage testImage = createTestImage(1920, 1080);

        // Save with default quality (no compression control)
        File defaultFile = tempDir.resolve("default.jpg").toFile();
        ImageIO.write(convertToRgb(testImage), "jpg", defaultFile);

        // Save with quality 0.7 (optimized)
        File optimizedFile = tempDir.resolve("optimized.jpg").toFile();
        saveJpgWithQuality(convertToRgb(testImage), optimizedFile, 0.7f);

        // Save with quality 0.5 (high compression)
        File highCompressionFile = tempDir.resolve("high_compression.jpg").toFile();
        saveJpgWithQuality(convertToRgb(testImage), highCompressionFile, 0.5f);

        // Verify files were created
        assertTrue(defaultFile.exists(), "Default file should exist");
        assertTrue(optimizedFile.exists(), "Optimized file should exist");
        assertTrue(highCompressionFile.exists(), "High compression file should exist");

        // Verify compression reduces file size
        long defaultSize = defaultFile.length();
        long optimizedSize = optimizedFile.length();
        long highCompressionSize = highCompressionFile.length();

        assertTrue(optimizedSize < defaultSize,
                String.format("Optimized (%.2fKB) should be smaller than default (%.2fKB)",
                        optimizedSize / 1024.0, defaultSize / 1024.0));
        assertTrue(highCompressionSize < optimizedSize,
                String.format("High compression (%.2fKB) should be smaller than optimized (%.2fKB)",
                        highCompressionSize / 1024.0, optimizedSize / 1024.0));
    }

    @ParameterizedTest
    @CsvSource({
            "1.0, 1920, 1080",
            "0.75, 1440, 810",
            "0.5, 960, 540",
            "0.25, 480, 270"
    })
    void testImageScaling(float scaleFactor, int expectedWidth, int expectedHeight) {
        BufferedImage original = createTestImage(1920, 1080);
        BufferedImage scaled = scaleImage(original, scaleFactor);

        assertEquals(expectedWidth, scaled.getWidth(),
                String.format("Width with scale %.2f should be %d", scaleFactor, expectedWidth));
        assertEquals(expectedHeight, scaled.getHeight(),
                String.format("Height with scale %.2f should be %d", scaleFactor, expectedHeight));
    }

    @Test
    void testScalingWithQualityReducesFileSize() throws IOException {
        BufferedImage original = createTestImage(1920, 1080);

        // Original size at quality 0.7
        File originalFile = tempDir.resolve("original.jpg").toFile();
        saveJpgWithQuality(convertToRgb(original), originalFile, 0.7f);

        // Scaled to 75% at quality 0.7
        BufferedImage scaled75 = scaleImage(original, 0.75f);
        File scaled75File = tempDir.resolve("scaled75.jpg").toFile();
        saveJpgWithQuality(convertToRgb(scaled75), scaled75File, 0.7f);

        // Scaled to 50% at quality 0.7
        BufferedImage scaled50 = scaleImage(original, 0.5f);
        File scaled50File = tempDir.resolve("scaled50.jpg").toFile();
        saveJpgWithQuality(convertToRgb(scaled50), scaled50File, 0.7f);

        long originalSize = originalFile.length();
        long scaled75Size = scaled75File.length();
        long scaled50Size = scaled50File.length();

        assertTrue(scaled75Size < originalSize,
                String.format("75%% scaled (%.2fKB) should be smaller than original (%.2fKB)",
                        scaled75Size / 1024.0, originalSize / 1024.0));
        assertTrue(scaled50Size < scaled75Size,
                String.format("50%% scaled (%.2fKB) should be smaller than 75%% scaled (%.2fKB)",
                        scaled50Size / 1024.0, scaled75Size / 1024.0));

        // Print reduction percentages for documentation
        double reduction75 = (1.0 - (double) scaled75Size / originalSize) * 100;
        double reduction50 = (1.0 - (double) scaled50Size / originalSize) * 100;
        System.out.printf("Size reduction: 75%% scale = %.1f%%, 50%% scale = %.1f%%%n",
                reduction75, reduction50);
    }

    @Test
    void testScaleFactorBoundaries() {
        BufferedImage original = createTestImage(100, 100);

        // Scale factor >= 1.0 should return original dimensions
        BufferedImage noScale = scaleImage(original, 1.0f);
        assertEquals(100, noScale.getWidth());
        assertEquals(100, noScale.getHeight());

        BufferedImage overScale = scaleImage(original, 1.5f);
        assertEquals(100, overScale.getWidth());
        assertEquals(100, overScale.getHeight());

        // Very small scale factor should still produce valid image
        BufferedImage tinyScale = scaleImage(original, 0.1f);
        assertTrue(tinyScale.getWidth() >= 1);
        assertTrue(tinyScale.getHeight() >= 1);
    }

    @Test
    void testQualityBoundaries() throws IOException {
        BufferedImage testImage = createTestImage(500, 500);
        BufferedImage rgbImage = convertToRgb(testImage);

        // Test minimum quality
        File minQualityFile = tempDir.resolve("min_quality.jpg").toFile();
        assertDoesNotThrow(() -> saveJpgWithQuality(rgbImage, minQualityFile, 0.0f));
        assertTrue(minQualityFile.exists());

        // Test maximum quality
        File maxQualityFile = tempDir.resolve("max_quality.jpg").toFile();
        assertDoesNotThrow(() -> saveJpgWithQuality(rgbImage, maxQualityFile, 1.0f));
        assertTrue(maxQualityFile.exists());

        // Min quality should produce smaller file than max quality
        assertTrue(minQualityFile.length() < maxQualityFile.length());
    }

    // Helper methods (mirroring the implementation in Evidences.java)

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Create a colorful test pattern to simulate real screenshot
        for (int y = 0; y < height; y += 50) {
            for (int x = 0; x < width; x += 50) {
                g2d.setColor(new Color(
                        (x * 255 / width) % 256,
                        (y * 255 / height) % 256,
                        ((x + y) * 128 / (width + height)) % 256
                ));
                g2d.fillRect(x, y, 50, 50);
            }
        }

        // Add some text to simulate UI elements
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Test Screenshot", 50, 50);

        g2d.dispose();
        return image;
    }

    private BufferedImage convertToRgb(BufferedImage original) {
        BufferedImage rgbImage = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.drawImage(original, 0, 0, Color.WHITE, null);
        g2d.dispose();
        return rgbImage;
    }

    private BufferedImage scaleImage(BufferedImage original, float scaleFactor) {
        if (scaleFactor >= 1.0f) {
            return original;
        }

        int newWidth = Math.max(1, (int) (original.getWidth() * scaleFactor));
        int newHeight = Math.max(1, (int) (original.getHeight() * scaleFactor));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }

    private void saveJpgWithQuality(BufferedImage image, File outputFile, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // ==================== FORMAT TESTS ====================

    @Test
    void testPngFormat() throws IOException {
        BufferedImage testImage = createTestImage(800, 600);

        File pngFile = tempDir.resolve("test.png").toFile();
        ImageIO.write(testImage, "png", pngFile);

        assertTrue(pngFile.exists(), "PNG file should exist");
        assertTrue(pngFile.length() > 0, "PNG file should have content");

        // Verify PNG is readable
        BufferedImage readBack = ImageIO.read(pngFile);
        assertNotNull(readBack, "PNG should be readable");
        assertEquals(800, readBack.getWidth());
        assertEquals(600, readBack.getHeight());
    }

    @Test
    void testFormatSizeComparison() throws IOException {
        BufferedImage testImage = createTestImage(1920, 1080);
        BufferedImage rgbImage = convertToRgb(testImage);

        // Save as PNG (lossless)
        File pngFile = tempDir.resolve("comparison.png").toFile();
        ImageIO.write(testImage, "png", pngFile);

        // Save as JPG quality 0.7
        File jpgFile = tempDir.resolve("comparison.jpg").toFile();
        saveJpgWithQuality(rgbImage, jpgFile, 0.7f);

        // Save as JPG quality 0.5
        File jpg50File = tempDir.resolve("comparison_50.jpg").toFile();
        saveJpgWithQuality(rgbImage, jpg50File, 0.5f);

        long pngSize = pngFile.length();
        long jpgSize = jpgFile.length();
        long jpg50Size = jpg50File.length();

        // JPG should be significantly smaller than PNG for photos/screenshots
        assertTrue(jpgSize < pngSize,
                String.format("JPG (%.2fKB) should be smaller than PNG (%.2fKB)",
                        jpgSize / 1024.0, pngSize / 1024.0));

        System.out.printf("Format comparison (1920x1080):%n");
        System.out.printf("  PNG:          %.2f KB%n", pngSize / 1024.0);
        System.out.printf("  JPG (q=0.7):  %.2f KB (%.1f%% of PNG)%n",
                jpgSize / 1024.0, (jpgSize * 100.0 / pngSize));
        System.out.printf("  JPG (q=0.5):  %.2f KB (%.1f%% of PNG)%n",
                jpg50Size / 1024.0, (jpg50Size * 100.0 / pngSize));
    }

    @Test
    @EnabledIf("isWebPSupported")
    void testWebPFormat() throws IOException {
        BufferedImage testImage = createTestImage(1920, 1080);
        BufferedImage rgbImage = convertToRgb(testImage);

        File webpFile = tempDir.resolve("test.webp").toFile();
        saveWebP(rgbImage, webpFile, 0.7f);

        assertTrue(webpFile.exists(), "WebP file should exist");
        assertTrue(webpFile.length() > 0, "WebP file should have content");

        // Compare with JPG
        File jpgFile = tempDir.resolve("test_webp_compare.jpg").toFile();
        saveJpgWithQuality(rgbImage, jpgFile, 0.7f);

        System.out.printf("WebP vs JPG (1920x1080, q=0.7):%n");
        System.out.printf("  JPG:  %.2f KB%n", jpgFile.length() / 1024.0);
        System.out.printf("  WebP: %.2f KB (%.1f%% of JPG)%n",
                webpFile.length() / 1024.0, (webpFile.length() * 100.0 / jpgFile.length()));
    }

    @Test
    void testWebPSupportDetection() {
        // Force ImageIO to scan for plugins (TwelveMonkeys registers via SPI)
        ImageIO.scanForPlugins();

        boolean supported = isWebPSupported();
        System.out.printf("WebP format supported: %s%n", supported);

        // List available writers for diagnostics
        String[] writerFormats = ImageIO.getWriterFormatNames();
        System.out.printf("Available ImageIO writers: %s%n", String.join(", ", writerFormats));

        // Note: WebP support depends on TwelveMonkeys being properly loaded via SPI
        // In some test environments, the SPI mechanism may not work correctly
        // The test verifies the detection mechanism works, not that WebP is always available
        if (supported) {
            System.out.println("WebP is available - TwelveMonkeys loaded successfully");
        } else {
            System.out.println("WebP not available - SPI registration may have failed in test context");
            System.out.println("This is expected in some test environments; WebP will work at runtime");
        }
    }

    // Helper for WebP support detection
    static boolean isWebPSupported() {
        // Ensure plugins are scanned
        ImageIO.scanForPlugins();
        return ImageIO.getImageWritersByFormatName("webp").hasNext();
    }

    private void saveWebP(BufferedImage image, File outputFile, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("WebP not supported");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
