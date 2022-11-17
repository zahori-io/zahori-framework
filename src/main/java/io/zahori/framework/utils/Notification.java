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

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class Notification {

    private JFrame frame;
    private JScrollPane scroll;

    private static final int DEFAULT_HEIGHT = 400;
    private static final int DEFAULT_WIDTH = 800;
    // private static final int DEFAULT_HEIGHT_TABLE = 1080;
    // private static final int DEFAULT_WIDTH_TABLE = 1920;
    private static final int DEFAULT_HEIGHT_TABLE = 720;
    private static final int DEFAULT_WIDTH_TABLE = 1280;
    private static final int MIN_COLUMN_WIDTH = 50;

    private static final String PROP_NOTIFICATION_BACKGROUNDS_PATH = "notification.backgrounds.path";

    public Notification(String text) {
        this(text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public Notification(String text, int pixelsWidth, int pixelsHeight) {
        try {
            initFrame(pixelsWidth, pixelsHeight);
            JTextArea notification = initTextNotification(text);
            this.frame.add(notification, BorderLayout.NORTH);
            showFrame(pixelsWidth, pixelsHeight);
        } catch (Exception e) {
            System.out.println("Unknown error when generating Zahori Notification");
        }

    }

    public Notification(String text, List<Map<String, String>> dataTable) {
        try {
            initFrame(DEFAULT_WIDTH_TABLE, DEFAULT_HEIGHT_TABLE);

            JTextArea notification = initTextNotification(text);
            this.frame.add(notification, BorderLayout.NORTH);
            showFrame(DEFAULT_WIDTH_TABLE, DEFAULT_HEIGHT_TABLE);

            JTable table = new JTable(new DataModel(dataTable));
            resizeColumnWidth(table);
            table.setOpaque(false);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            this.scroll = new JScrollPane(table);
            this.scroll.setPreferredSize(new Dimension(DEFAULT_WIDTH_TABLE - 100, DEFAULT_HEIGHT_TABLE - 100));
            this.scroll.setOpaque(false);
            this.frame.add(scroll, BorderLayout.CENTER);

            showFrame(DEFAULT_WIDTH_TABLE, DEFAULT_HEIGHT_TABLE);

        } catch (Exception e) {
            System.out.println("Unknown error when generating Zahori Notification");
        }

    }

    public void scroll() {
        try {
            Point actualView = this.scroll.getViewport().getViewPosition();
            actualView.setLocation(actualView.getX() + 1.0, actualView.getY());
            this.scroll.getViewport().setViewPosition(actualView);
        } catch (Exception e) {
            System.out.println("Unknown error when scrolling Zahori Notification");
        }
    }

    public boolean isScrollable() {
        try {
            JScrollBar scrollBar = this.scroll.getHorizontalScrollBar();
            return (scrollBar.getValue() < (scrollBar.getMaximum() - scrollBar.getVisibleAmount()));
        } catch (Exception e) {
            return false;
        }
    }

    public void closeNotification() {
        try {
            frame.setVisible(false);
            frame.dispose();
        } catch (Exception e) {
            System.out.println("Unknown error when trying to close Zahori Notification");
        }

    }

    private void initFrame(int pixelsWidth, int pixelsHeight) {
        this.frame = new JFrame();
        this.frame.setSize(pixelsWidth, pixelsHeight);
        this.frame.setLocationRelativeTo(null);
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setUndecorated(true);
        this.frame.setVisible(false);
        this.frame.setLayout(new BorderLayout());
        this.frame.getRootPane().setBackground(new Color(219, 219, 219));
        this.frame.getRootPane().setBorder(BorderFactory.createMatteBorder(4, 4, 4, 4, Color.DARK_GRAY));
        String nameImage = "background_" + pixelsWidth + "x" + pixelsHeight + ".png";
        String pathImageFile = System.getProperty(PROP_NOTIFICATION_BACKGROUNDS_PATH);
        this.frame.setContentPane(new JLabel(new ImageIcon(pathImageFile + nameImage)));
        this.frame.setLayout(new BorderLayout());
        this.frame.setSize(pixelsWidth - 1, pixelsHeight - 1);
        this.frame.setSize(pixelsWidth, pixelsHeight);
    }

    private JTextArea initTextNotification(String text) {
        JTextArea notification = new JTextArea();
        notification.setEditable(false);
        notification.setBackground(Color.DARK_GRAY);
        notification.setCaretColor(Color.DARK_GRAY);
        notification.setForeground(Color.DARK_GRAY);
        notification.setMargin(new Insets(20, 20, 20, 20));
        notification.setLineWrap(true);
        notification.setText(text);
        notification.setOpaque(false);

        return notification;
    }

    private void showFrame(int pixelsWidth, int pixelsHeight) {
        this.frame.setVisible(true);
        this.frame.setSize(pixelsWidth - 1, pixelsHeight - 1);
        this.frame.setSize(pixelsWidth, pixelsHeight);
    }

    private void resizeColumnWidth(JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = MIN_COLUMN_WIDTH;
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 1, width);
            }
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    static class DataModel extends AbstractTableModel {

        private static final long serialVersionUID = -1422025101140468579L;

        Object[][] dataTable;
        String[] headers;

        // This class prints data in console every time there are changes on any
        // table cell.
        class TableListener implements TableModelListener {
            @Override
            public void tableChanged(TableModelEvent evt) {
                for (Object[] aDataTable : dataTable) {
                    for (int j = 0; j < dataTable[0].length; j++) {
                        System.out.print(aDataTable[j] + " ");
                    }
                    System.out.println();
                }
            }
        }

        // Constructor
        DataModel(List<Map<String, String>> data) {
            if (data.isEmpty() || (data == null)) {
                this.dataTable = new Object[0][0];
            } else {
                this.dataTable = new Object[data.size()][data.get(0).size()];
                this.headers = new String[data.get(0).size()];
                Object[] keyList = data.get(0).keySet().toArray();
                for (int i = 0; i < data.get(0).size(); i++) {
                    this.headers[i] = keyList[i].toString().trim();
                }

                for (int i = 0; i < data.size(); i++) {
                    for (int j = 0; j < keyList.length; j++) {
                        this.dataTable[i][j] = data.get(i).get(keyList[j].toString().trim());
                    }
                }

                addTableModelListener(new TableListener());
            }
        }

        @Override
        public String getColumnName(int index) {
            return headers[index];
        }

        @Override
        public int getColumnCount() {
            if (dataTable.length > 0) {
                return (dataTable[0].length);
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return (dataTable.length);
        }

        @Override
        public Object getValueAt(int row, int col) {
            return (dataTable[row][col]);
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            dataTable[row][col] = value;
            fireTableDataChanged();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return (false);
        }
    }

}
