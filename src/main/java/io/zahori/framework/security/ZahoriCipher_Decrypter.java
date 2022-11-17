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

public class ZahoriCipher_Decrypter extends javax.swing.JFrame {

    private static final long serialVersionUID = 1222719892838685366L;

    private javax.swing.JLabel sourceTextLabel;
    private javax.swing.JButton cipherButton;
    private javax.swing.JTextArea resultLabel;
    private javax.swing.JTextField sourceTextField;

    public ZahoriCipher_Decrypter() {
        initComponents();
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ZahoriCipher_Decrypter().setVisible(true);
            }
        });
    }

    private void initComponents() {
        sourceTextField = new javax.swing.JTextField(20);
        sourceTextLabel = new javax.swing.JLabel();
        cipherButton = new javax.swing.JButton();
        resultLabel = new javax.swing.JTextArea(1, 30);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Zahori Cipher");

        sourceTextLabel.setText("Please, enter the text to be decrypted.");

        cipherButton.setText("Decrypt it !");
        cipherButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cipherButtonActionPerformed(evt);
            }
        });

        sourceTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                if (sourceTextField.getText().length() >= 25 && !(evt.getKeyChar() == KeyEvent.VK_DELETE || evt.getKeyChar() == KeyEvent.VK_BACK_SPACE)) {
                    getToolkit().beep();
                    evt.consume();
                }
            }
        });

        resultLabel.setText("");
        resultLabel.setEditable(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
	    		layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
	            	.addGroup(layout.createSequentialGroup()
	            			.addContainerGap()
	            			.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
	            					.addGroup(layout.createSequentialGroup()
	            							.addComponent(sourceTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
	            							.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
	            							.addComponent(sourceTextLabel))
	            							.addGroup(layout.createSequentialGroup()
	            									.addComponent(cipherButton)
	            									.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
	            									.addComponent(resultLabel)))
	            									.addContainerGap(27, Short.MAX_VALUE))
	    		);

        ImageIcon img = new ImageIcon("src/main/resources/icono_jframe.png");
        setIconImage(img.getImage());
        setResizable(false);

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, cipherButton, sourceTextField);
        layout.setVerticalGroup(
	            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
	            	  .addGroup(layout.createSequentialGroup()
	            			  .addContainerGap()
	            			  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
	            					  .addComponent(sourceTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
	            					  .addComponent(sourceTextLabel))
	            					  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
	            					  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
	            							  .addComponent(cipherButton)
	            							  .addComponent(resultLabel))
	            							  .addContainerGap(21, Short.MAX_VALUE))
	    		);
	        	pack();
	    }

    private void cipherButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String result;
        try {
            ZahoriCipher cipher = new ZahoriCipher();
            result = cipher.decode(sourceTextField.getText());
        } catch (Exception e) {
            result = "A problem has been found on encryption process.";
            System.out.println(e.getMessage());
        }

        resultLabel.setText(result);
    }

}
