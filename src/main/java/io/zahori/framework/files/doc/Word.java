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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
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
 * The type Word.
 */
public class Word {

    private WordprocessingMLPackage wordMLPackage;
    private String directorio;
    private String nombre;

    /**
     * Instantiates a new Word.
     *
     * @param directorio the directorio
     * @param nombre     the nombre
     * @param titulo     the titulo
     */
    public Word(String directorio, String nombre, String titulo) {

        this.directorio = directorio;
        this.nombre = nombre;

        try {
            // Creamos el documento
            wordMLPackage = WordprocessingMLPackage.createPackage();

            // Insertamos el título del documento
            if (!StringUtils.isBlank(titulo)) {
                wordMLPackage.getMainDocumentPart().addParagraphOfText(titulo);
            }

            // Guardamos el documento
            wordMLPackage.save(new java.io.File(directorio, nombre));
        } catch (Exception e) {
            throw new RuntimeException("Error creating evidence doc: " + e.getMessage());
        }
    }

    /**
     * Instantiates a new Word.
     *
     * @param directorio   the directorio
     * @param nombre       the nombre
     * @param titulo       the titulo
     * @param templatePath the template path
     */
    public Word(String directorio, String nombre, String titulo, String templatePath) {

        this.directorio = directorio;
        this.nombre = nombre;

        try {
            // Creamos el documento
            wordMLPackage = WordprocessingMLPackage.load(new FileInputStream(new File(templatePath)));

            // Insertamos el título del documento
            if (!StringUtils.isBlank(titulo)) {
                wordMLPackage.getMainDocumentPart().addParagraphOfText(titulo);
            }

            // Guardamos el documento
            wordMLPackage.save(new java.io.File(directorio, nombre));
        } catch (Exception e) {
            throw new RuntimeException("Error creating evidence doc: " + e.getMessage());
        }
    }

    /**
     * Insertar texto.
     *
     * @param texto the texto
     */
    public void insertarTexto(String texto) {
        insertText(texto, null, false);
        saveDoc();
    }

    /**
     * Insertar texto negrita.
     *
     * @param texto the texto
     */
    public void insertarTextoNegrita(String texto) {
        insertText(texto, null, true);
        saveDoc();
    }

    /**
     * Insertar texto color.
     *
     * @param texto the texto
     * @param color the color
     */
    public void insertarTextoColor(String texto, String color) {
        insertText(texto, color, false);
        saveDoc();
    }

    /**
     * Insertar texto color negrita.
     *
     * @param texto the texto
     * @param color the color
     */
    public void insertarTextoColorNegrita(String texto, String color) {
        insertText(texto, color, true);
        saveDoc();
    }

    /**
     * Insertar imagen.
     *
     * @param imagen the imagen
     * @param titulo the titulo
     */
    public void insertarImagen(File imagen, String titulo) {
        insertText(titulo, null, false);
        insertarImagen(imagen);
    }

    /**
     * Insertar imagen color.
     *
     * @param imagen the imagen
     * @param titulo the titulo
     * @param color  the color
     */
    public void insertarImagenColor(File imagen, String titulo, String color) {
        insertText(titulo, color, false);
        insertarImagen(imagen);
    }

    /**
     * Insertar imagen negrita.
     *
     * @param imagen the imagen
     * @param titulo the titulo
     */
    public void insertarImagenNegrita(File imagen, String titulo) {
        insertText(titulo, null, true);
        insertarImagen(imagen);
    }

    /**
     * Insertar imagen color negrita.
     *
     * @param imagen the imagen
     * @param titulo the titulo
     * @param color  the color
     */
    public void insertarImagenColorNegrita(File imagen, String titulo, String color) {
        insertText(titulo, color, true);
        insertarImagen(imagen);
    }

    private void insertarImagen(File imagen) {
        try {
            byte[] bytes = convertImageToByteArray(imagen);
            addImageToPackage(wordMLPackage, bytes);

        } catch (Exception e) {
            throw new RuntimeException("Error writing image in evidence document: " + e.getMessage());
        }

        saveDoc();
    }

    private void insertText(String text, String colorValue, boolean bold) {
        if (text != null) {

            text = StringUtils.replace(text, "\n", " \n");
            String[] split = StringUtils.split(text, "\n");
            if (split.length > 0) {
                try {
                    for (String aSplit : split) {
                        ObjectFactory factory = Context.getWmlObjectFactory();
                        P para = factory.createP();
                        R run = factory.createR();
                        Text t = factory.createText();
                        RPr rpr = factory.createRPr();

                        if (!StringUtils.isEmpty(colorValue)) {
                            Color color = factory.createColor();
                            color.setVal(colorValue);
                            rpr.setColor(color);
                        }

                        if (bold) {
                            BooleanDefaultTrue b = new BooleanDefaultTrue();
                            b.setVal(Boolean.TRUE);
                            rpr.setB(b);
                        }

                        t.setValue(aSplit);
                        run.getContent().add(t); // ContentAccessor

                        if (!StringUtils.isEmpty(colorValue) || bold) {
                            run.setRPr(rpr);
                        }

                        para.getContent().add(run); // ContentAccessor
                        wordMLPackage.getMainDocumentPart().addObject(para);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error writing text in evidence document: " + e.getMessage());
                }
            }
        }
    }

    private void saveDoc() {
        try {
            wordMLPackage.save(new java.io.File(directorio, nombre));
        } catch (Exception e) {
            throw new RuntimeException("Error saving evidence document: " + e.getMessage());
        }
    }

    /**
     * Gets directorio.
     *
     * @return the directorio
     */
    public String getDirectorio() {
        return directorio;
    }

    /**
     * Gets nombre.
     *
     * @return the nombre
     */
    public String getNombre() {
        return nombre;
    }

    // ////////////////////////////// PRIVATE METHODS
    // ////////////////////////////////

    private static void addImageToPackage(WordprocessingMLPackage wordMLPackage, byte[] bytes) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordMLPackage, bytes);

        int docPrId = 1;
        int cNvPrId = 2;
        Inline inline = imagePart.createImageInline("Filename hint", "Alternative text", docPrId, cNvPrId, false);

        P paragraph = addInlineImageToParagraph(inline);

        wordMLPackage.getMainDocumentPart().addObject(paragraph);
    }

    private static P addInlineImageToParagraph(Inline inline) {
        // Now add the in-line image to a paragraph
        ObjectFactory factory = new ObjectFactory();
        P paragraph = factory.createP();
        R run = factory.createR();
        paragraph.getContent().add(run);
        Drawing drawing = factory.createDrawing();
        run.getContent().add(drawing);
        drawing.getAnchorOrInline().add(inline);
        return paragraph;
    }

    private static byte[] convertImageToByteArray(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        long length = file.length();
        // You cannot create an array using a long, it needs to be an int.
        if (length > Integer.MAX_VALUE) {
            System.out.println("File too large!!");
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        int numRead;
        while ((offset < bytes.length) && ((numRead = is.read(bytes, offset, bytes.length - offset)) >= 0)) {
            offset += numRead;
        }
        // Ensure all the bytes have been read
        if (offset < bytes.length) {
            System.out.println("Could not completely read file " + file.getName());
        }
        is.close();
        return bytes;
    }

}
