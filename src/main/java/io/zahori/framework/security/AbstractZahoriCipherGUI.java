package io.zahori.framework.security;

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

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Abstract base class for Zahori Cipher GUI components.
 * Eliminates code duplication between encryption and decryption GUIs.
 */
public abstract class AbstractZahoriCipherGUI extends JFrame {

    private static final long serialVersionUID = 1222719892838685366L;
    private static final int MAX_INPUT_LENGTH = 25;

    protected JTextField sourceTextField;
    protected JTextArea resultLabel;

    protected AbstractZahoriCipherGUI() {
        initComponents();
    }

    /**
     * Returns the label text for the input field.
     */
    protected abstract String getInputLabelText();

    /**
     * Returns the button text.
     */
    protected abstract String getButtonText();

    /**
     * Processes the input text (encrypt or decrypt).
     */
    protected abstract String processText(String input) throws Exception;

    /**
     * Returns the error message for processing failures.
     */
    protected abstract String getErrorMessage();

    private void initComponents() {
        sourceTextField = new JTextField(20);
        JLabel sourceTextLabel = new JLabel();
        JButton cipherButton = new JButton();
        resultLabel = new JTextArea(1, 30);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Zahori Cipher");

        sourceTextLabel.setText(getInputLabelText());
        cipherButton.setText(getButtonText());
        cipherButton.addActionListener(this::cipherButtonActionPerformed);

        sourceTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent evt) {
                if (sourceTextField.getText().length() >= MAX_INPUT_LENGTH
                        && evt.getKeyChar() != KeyEvent.VK_DELETE
                        && evt.getKeyChar() != KeyEvent.VK_BACK_SPACE) {
                    getToolkit().beep();
                    evt.consume();
                }
            }
        });

        resultLabel.setText("");
        resultLabel.setEditable(false);

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);

        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(sourceTextField, GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(sourceTextLabel))
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(cipherButton)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(resultLabel)))
                        .addContainerGap(27, Short.MAX_VALUE)));

        ImageIcon img = new ImageIcon("src/main/resources/icono_jframe.png");
        setIconImage(img.getImage());
        setResizable(false);

        layout.linkSize(SwingConstants.HORIZONTAL, cipherButton, sourceTextField);

        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(sourceTextField, GroupLayout.PREFERRED_SIZE,
                                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addComponent(sourceTextLabel))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(cipherButton)
                                .addComponent(resultLabel))
                        .addContainerGap(21, Short.MAX_VALUE)));
        pack();
    }

    @SuppressWarnings("PMD.UnusedFormalParameter") // ActionListener API requirement
    private void cipherButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String result;
        try {
            result = processText(sourceTextField.getText());
        } catch (Exception e) {
            result = getErrorMessage();
            System.out.println(e.getMessage());
        }
        resultLabel.setText(result);
    }
}
