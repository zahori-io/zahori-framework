package io.zahori.framework.files.doc;

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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.docx4j.Docx4J;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.Color;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;

/**
 * Utilidad para generación de documentos Word (.docx) usando docx4j.
 *
 * <p>Implementa AutoCloseable para garantizar liberación de recursos.
 * Uso recomendado con try-with-resources:</p>
 *
 * <pre>{@code
 * try (Word doc = new Word("output", "evidence.docx", "Test Results")) {
 *     doc.insertarTexto("Step 1 passed");
 *     doc.insertarImagen(screenshot, "Screenshot");
 * }
 * }</pre>
 *
 * @since docx4j 11.5.0 / JDK 17
 */
public class Word implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(Word.class);
    private static final String RED = "FF0000";
    private static final ObjectFactory FACTORY = Context.getWmlObjectFactory();

    private final WordprocessingMLPackage wordMLPackage;
    private final String directorio;
    private final String nombre;

    /**
     * Crea un nuevo documento Word vacío.
     *
     * @param directorio Directorio de salida
     * @param nombre     Nombre del archivo (ej: "evidence.docx")
     * @param titulo     Título inicial del documento (puede ser null)
     */
    public Word(String directorio, String nombre, String titulo) {
        this.directorio = directorio;
        this.nombre = nombre;

        try {
            this.wordMLPackage = WordprocessingMLPackage.createPackage();

            if (StringUtils.isNotBlank(titulo)) {
                wordMLPackage.getMainDocumentPart().addParagraphOfText(titulo);
            }

            saveDoc();
            LOG.debug("Documento Word creado: {}/{}", directorio, nombre);
        } catch (Docx4JException e) {
            LOG.error("Error creando documento Word: {}", e.getMessage());
            throw new RuntimeException("Error creating evidence doc: " + e.getMessage(), e);
        }
    }

    /**
     * Crea un documento Word basado en una plantilla existente.
     *
     * @param directorio   Directorio de salida
     * @param nombre       Nombre del archivo de salida
     * @param titulo       Título adicional (puede ser null)
     * @param templatePath Ruta a la plantilla .docx
     */
    public Word(String directorio, String nombre, String titulo, String templatePath) {
        this.directorio = directorio;
        this.nombre = nombre;

        try (FileInputStream fis = new FileInputStream(new File(templatePath))) {
            this.wordMLPackage = WordprocessingMLPackage.load(fis);

            if (StringUtils.isNotBlank(titulo)) {
                wordMLPackage.getMainDocumentPart().addParagraphOfText(titulo);
            }

            saveDoc();
            LOG.debug("Documento Word creado desde plantilla: {}", templatePath);
        } catch (IOException e) {
            LOG.error("Error leyendo plantilla {}: {}", templatePath, e.getMessage());
            throw new RuntimeException("Error reading template: " + e.getMessage(), e);
        } catch (Docx4JException e) {
            LOG.error("Error procesando plantilla {}: {}", templatePath, e.getMessage());
            throw new RuntimeException("Error creating evidence doc: " + e.getMessage(), e);
        }
    }

    /**
     * Cierra el documento y libera recursos.
     */
    @Override
    public void close() {
        // WordprocessingMLPackage no tiene close() explícito,
        // pero liberamos la referencia para GC
        LOG.debug("Documento Word cerrado: {}/{}", directorio, nombre);
    }

    public void insertarTexto(String texto) {
        insertText(texto, null, false);
        saveDoc();
    }

    public void insertarTextoNegrita(String texto) {
        insertText(texto, null, true);
        saveDoc();
    }

    public void insertarTextoColor(String texto, String color) {
        insertText(texto, color, false);
        saveDoc();
    }

    public void insertarTextoColorNegrita(String texto, String color) {
        insertText(texto, color, true);
        saveDoc();
    }

    public void insertarImagen(File imagen, String titulo) {
        insertText(titulo, null, false);
        insertarImagen(imagen);
    }

    public void insertarImagenColor(File imagen, String titulo, String color) {
        insertText(titulo, color, false);
        insertarImagen(imagen);
    }

    public void insertarImagenNegrita(File imagen, String titulo) {
        insertText(titulo, null, true);
        insertarImagen(imagen);
    }

    public void insertarImagenColorNegrita(File imagen, String titulo, String color) {
        insertText(titulo, color, true);
        insertarImagen(imagen);
    }

    private void insertarImagen(File imagen) {
        try {
            byte[] bytes = Files.readAllBytes(imagen.toPath());
            addImageToPackage(bytes);
        } catch (IOException e) {
            LOG.warn("Error leyendo imagen {}: {}", imagen.getName(), e.getMessage());
            insertarTextoColor("Error reading image: " + e.getMessage(), RED);
        } catch (Exception e) {
            LOG.warn("Error insertando imagen {}: {}", imagen.getName(), e.getMessage());
            insertarTextoColor("Error writing image in evidence document: " + e.getMessage(), RED);
        }
        saveDoc();
    }

    private void insertText(String text, String colorValue, boolean bold) {
        if (text == null) {
            return;
        }

        String normalizedText = StringUtils.replace(text, "\n", " \n");
        String[] lines = StringUtils.split(normalizedText, "\n");

        if (lines == null || lines.length == 0) {
            return;
        }

        for (String line : lines) {
            P para = FACTORY.createP();
            R run = FACTORY.createR();
            Text t = FACTORY.createText();
            t.setValue(line);
            run.getContent().add(t);

            if (StringUtils.isNotEmpty(colorValue) || bold) {
                RPr rpr = FACTORY.createRPr();

                if (StringUtils.isNotEmpty(colorValue)) {
                    Color color = FACTORY.createColor();
                    color.setVal(colorValue);
                    rpr.setColor(color);
                }

                if (bold) {
                    BooleanDefaultTrue b = new BooleanDefaultTrue();
                    b.setVal(Boolean.TRUE);
                    rpr.setB(b);
                }

                run.setRPr(rpr);
            }

            para.getContent().add(run);
            wordMLPackage.getMainDocumentPart().addObject(para);
        }
    }

    private void saveDoc() {
        try {
            Docx4J.save(wordMLPackage, new File(directorio, nombre));
        } catch (Docx4JException e) {
            LOG.error("Error guardando documento {}/{}: {}", directorio, nombre, e.getMessage());
            throw new RuntimeException("Error saving evidence document: " + e.getMessage(), e);
        }
    }

    public String getDirectorio() {
        return directorio;
    }

    public String getNombre() {
        return nombre;
    }

    private void addImageToPackage(byte[] bytes) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordMLPackage, bytes);

        int docPrId = 1;
        int cNvPrId = 2;
        Inline inline = imagePart.createImageInline("Filename hint", "Alternative text", docPrId, cNvPrId, false);

        P paragraph = createImageParagraph(inline);
        wordMLPackage.getMainDocumentPart().addObject(paragraph);
    }

    private P createImageParagraph(Inline inline) {
        P paragraph = FACTORY.createP();
        R run = FACTORY.createR();
        paragraph.getContent().add(run);
        Drawing drawing = FACTORY.createDrawing();
        run.getContent().add(drawing);
        drawing.getAnchorOrInline().add(inline);
        return paragraph;
    }
}
