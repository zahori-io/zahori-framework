package io.zahori.framework.database;

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

import io.zahori.framework.utils.Notification;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataBase {

    private static final Logger LOG = LogManager.getLogger(DataBase.class);
    private static final String ERROR_EXECUTING_QUERY = "Error executing query -> ";

    private final String driver;
    private final String url;
    private final String user;
    private final String password;

    public DataBase(String driver, String url, String user, String password) {
        this.driver = driver;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        string.append("Database parameters [");
        string.append("driver: ").append(driver);
        string.append(", url: ").append(url);
        string.append(", user: ").append(user);
        string.append(", password: ").append(password);
        string.append("]");
        return string.toString();
    }

    @Deprecated
    public Map<String, String> executeQuery(String query, Object... args) {

        long startTime = System.currentTimeMillis();

        // Validate parameters
        if (StringUtils.isBlank(driver) || StringUtils.isBlank(url) || StringUtils.isBlank(user) || StringUtils.isBlank(password)
                || StringUtils.isBlank(query)) {
            throw new RuntimeException(ERROR_EXECUTING_QUERY + "invalid parameters: " + this + " Query: " + query);
        }

        LOG.debug("Executing query");

        try {
            // Load class into memory
            Class.forName(driver); // throws ClassNotFoundException
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException(ERROR_EXECUTING_QUERY + "Driver not found: " + cnfe.getMessage(), cnfe);
        }

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement prepStmt = connection.prepareStatement(query)) {

            if ((args != null) && (args.length > 0)) {
                for (int i = 0; i < args.length; i++) {
                    prepStmt.setObject(i + 1, args[i]);
                }
            }

            try (ResultSet resultSet = prepStmt.executeQuery()) {
                LOG.debug("Query executed successfully! [time: {} ms]", System.currentTimeMillis() - startTime);

                Map<String, String> map = new HashMap<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int colCount = metaData.getColumnCount();

                while (resultSet.next()) {
                    LOG.info("--- Database row ---");
                    for (int i = 0; i < colCount; i++) {
                        LOG.info("{}: {}", metaData.getColumnName(i + 1), resultSet.getString(i + 1));
                        map.put(metaData.getColumnName(i + 1), resultSet.getString(i + 1));
                    }
                }
                return map;
            }

        } catch (SQLException sqle) {
            throw new RuntimeException(ERROR_EXECUTING_QUERY + sqle.getMessage(), sqle);
        }
    }

    /*
     * executes a query. Returns a list with all rows returned by the query
     */
    public List<Map<String, String>> execute(String query, Object... args) {
        return executeGeneric(true, query, args);
    }

    /*
     * executes a query. Returns a list with all rows returned by the query
     */
    public List<Map<String, String>> tryToExecute(String query, Object... args) {
        return executeGeneric(false, query, args);
    }

    /*
     * executes a query. Returns a list with all rows returned by the query
     */
    private List<Map<String, String>> executeGeneric(boolean flagThrowException, String query, Object... args) {

        long startTime = System.currentTimeMillis();

        // Validate parameters
        if (StringUtils.isBlank(driver) || StringUtils.isBlank(url) || StringUtils.isBlank(user) || StringUtils.isBlank(query)) {
            throw new RuntimeException(ERROR_EXECUTING_QUERY + "invalid parameters: " + this + " Query: " + query);
        }

        LOG.debug("Executing query");

        try {
            // Load class into memory
            Class.forName(driver); // throws ClassNotFoundException
        } catch (ClassNotFoundException cnfe) {
            if (flagThrowException) {
                throw new RuntimeException(ERROR_EXECUTING_QUERY + "Driver not found: " + cnfe.getMessage(), cnfe);
            } else {
                return new ArrayList<>();
            }
        }

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement prepStmt = prepareQuery(connection, query, args)) {

            List<Map<String, String>> results = new ArrayList<>();

            if (StringUtils.startsWithIgnoreCase(query, "update")) {
                prepStmt.executeUpdate();
                connection.commit();
            } else if (StringUtils.startsWithIgnoreCase(query, "select")) {
                try (ResultSet resultSet = prepStmt.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int colCount = metaData.getColumnCount();

                    while (resultSet.next()) {
                        Map<String, String> row = new HashMap<>();
                        LOG.info("--- Database row ---");
                        for (int i = 0; i < colCount; i++) {
                            LOG.info("{}: {}", metaData.getColumnName(i + 1), resultSet.getString(i + 1));
                            row.put(metaData.getColumnName(i + 1), resultSet.getString(i + 1));
                        }
                        results.add(row);
                    }
                }
            }

            LOG.debug("Query executed successfully! [time: {} ms]", System.currentTimeMillis() - startTime);
            return results;

        } catch (SQLException sqle) {
            if (flagThrowException) {
                throw new RuntimeException(ERROR_EXECUTING_QUERY + sqle.getMessage(), sqle);
            } else {
                return new ArrayList<>();
            }
        }
    }

    public List<Map<String, String>> executeAndNotify(String txtQuery, Object... args) {
        List<Map<String, String>> resultsMap = execute(txtQuery, args);
        String query = txtQuery;
        for (Object arg : args) {
            query = query.replaceFirst("\\?", String.valueOf(arg));
        }

        try {

            String mensaje = "Se ha ejecutado la siguiente query:\n" + query + "\n";
            Notification notification = new Notification(mensaje, resultsMap);
            while (notification.isScrollable()) {
                Thread.sleep(5L);
                notification.scroll();
            }
            Thread.sleep(2000L);

            notification.closeNotification();
        } catch (InterruptedException e) {
            throw new RuntimeException(ERROR_EXECUTING_QUERY + query);
        }

        return resultsMap;
    }

    private PreparedStatement prepareQuery(Connection connection, String txtQuery, Object... args) throws SQLException {
        PreparedStatement query = connection.prepareStatement(txtQuery);
        if ((args != null) && (args.length > 0)) {
            for (int i = 0; i < args.length; i++) {
                query.setObject(i + 1, args[i]);
            }
        }

        return query;
    }
}
