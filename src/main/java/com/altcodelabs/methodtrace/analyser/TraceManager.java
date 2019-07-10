/*
 * Copyright 2019 Aniruddha Sarkar.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.altcodelabs.methodtrace.analyser;

import com.ibm.jvm.trace.format.api.TraceContext;
import com.ibm.jvm.trace.format.api.TracePointImpl;
import com.ibm.jvm.trace.format.api.TraceThread;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.table.DefaultTableModel;

/**
 * Used to handle the traces in the workspace.
 * It primarily uses SQLITE databases to store the traces and perform operations on it.
 * @author ani
 */
public class TraceManager {

    HashMap<String, Connection> connects;

    /**
     * Creates an instance of the trace file.
     */
    public TraceManager() {
        connects = new HashMap();
    }
    
    /**
     * Scans the files with '.fmt.db' extension, creates JDBC connection of the scanned 
     * database files and stores them in connects variable.
     */
    public void loadTraces() {
        File file = new File(Preferences.getPreferences().config.get("ws"));
        for (File file1 : file.listFiles((File dir, String name) -> name.toLowerCase().endsWith(".fmt.db") //To change body of generated methods, choose Tools | Templates.
        )) {
            if (loadTrace(file1.getName().substring(0, file1.getName().length() - 7), file1.getAbsolutePath())) {
                System.out.println("Loaded: " + file1);
            } else {
                System.out.println("Not loaded: " + file1);
            }
        }
    }

    /**
     * Creates JDBC connection of the 
     * database file and stores them in connects variable.
     * @param name the name of the trace
     * @param path the path to the database file
     * @return true if successful
     */
    private boolean loadTrace(String name, String path) {
        try {
            Connection con = DriverManager.getConnection("jdbc:sqlite:" + path);

            if (con != null) {
                Statement stmt = con.createStatement();
                ResultSet rs4 = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='info';");

                if (rs4.next()) {
                    connects.put(name, con);
                    stmt.close();
                    return true;
                } else {
                    stmt.close();
                    return false;
                }
            } else {
                return false;
            }
        } catch (SQLException ex) {
            Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    /**
     * Used to define a method invocation in a trace
     */
    public static class MethodTrace {

        public String className;
        public int stack_level;
        public String methodName;
        public String parameters;
        public String definition;
        public String[] tp_ids;
        public boolean isCompleted;
        public String stackTrace;
        public long timeIn;
        public long timeOut;
        public boolean hasException;

        @Override
        public boolean equals(Object o) {
            return o instanceof MethodTrace && this.hashCode() == o.hashCode(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String toString() {
            return className + " " + methodName + " " + stack_level + " " + hasException; //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.className);
            hash = 59 * hash + this.stack_level;
            hash = 59 * hash + Objects.hashCode(this.methodName);
            hash = 59 * hash + Objects.hashCode(this.parameters);
            return hash;
        }

    }

    /**
     * Import a trace file, by converting it to a database file, into the workspace directory.
     * @param input_files The files to import
     * @param name the name of the trace
     * @return true if successful
     */
    public boolean importTrace(List<File> input_files, String name) {
        try {

            TraceContextFactory factory = new TraceContextFactory(input_files);
            if (factory.loadFile()) {
                File output = new File(Preferences.getPreferences().config.get("ws") + File.separator + name + ".fmt.db");
                if (output.exists()) {
                    output.delete();
                }
                try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + output.getAbsolutePath())) {
                    con.setAutoCommit(false);
                    Statement stmt = con.createStatement();

                    TraceContext context = factory.getContext();

                    stmt.addBatch("CREATE TABLE info(key VARCHAR(16),value TEXT);");

                    stmt.addBatch("INSERT INTO info VALUES('name','" + name + "');");
                    stmt.addBatch("INSERT INTO info VALUES('summary','" + context.summary() + "');");
                    stmt.addBatch("INSERT INTO info VALUES('vm','" + context.getVmVersionString() + "');");

                    stmt.addBatch("CREATE TABLE thread("
                            + "id INTEGER,"
                            + "native_id INTEGER,"
                            + "name TEXT"
                            + ");");

                    stmt.addBatch("CREATE TABLE point("
                            + "tp_id INTEGER,"
                            + "component TEXT,"
                            + "container_component TEXT, "
                            + "debug_info TEXT,"
                            + "formatted_parameters TEXT,"
                            + "parameters TEXT,"
                            + "formatted_time TEXT,"
                            + "t_time TEXT,"
                            + "groups TEXT,"
                            + "type TEXT,"
                            + "thread_id INTEGER"
                            + ");");

                    context.getThreads().forEachRemaining((object) -> {
                        try {
                            TraceThread thread = (TraceThread) object;
                            stmt.addBatch("INSERT INTO thread VALUES("
                                    + thread.getThreadID() + ","
                                    + thread.getNativeThreadID() + ",'"
                                    + sanitize(thread.getThreadName()) + "');");
                            thread.getIterator().forEachRemaining((object2) -> {
                                try {
                                    //Add threads to SQLITE
                                    TracePointImpl point = (TracePointImpl) object2;

                                    stmt.addBatch("INSERT INTO point VALUES("
                                            + point.getTPID() + ",'"
                                            + sanitize(point.getComponentName()) + "','"
                                            + sanitize(point.getContainerComponentName()) + "','"
                                            + sanitize(point.getDebugInfo()) + "','"
                                            + sanitize(point.getFormattedParameters()) + "','"
                                            + sanitize(spaceSeparate("||", 3, point.getParameters())) + "','"
                                            + sanitize(point.getFormattedTime()) + "','"
                                            + sanitize(point.getRawTime().toString()) + "','"
                                            + sanitize(spaceSeparate(",", (Object[]) point.getGroups())) + "','"
                                            + sanitize(point.getType()) + "','"
                                            + thread.getThreadID()
                                            + "');");
                                } catch (SQLException ex) {
                                    Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            });
                        } catch (SQLException ex) {
                            Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                    int[] res = stmt.executeBatch();
                    for (Integer i : res) {
                        if (i == Statement.EXECUTE_FAILED) {
                            return false;
                        }
                    }
                    con.commit();
                    connects.put(name, con);
                    return true;

                } catch (SQLException ex) {

                    Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, e);

            return false;
        }
    }

    /**
     * Enlists the Method Invocations in a trace
     * @param name the name of the trace file
     * @return the list containing entries, null if no trace, with the name, found.
     */
    public List<MethodTrace> getMethodTrace(String name) {
        if (connects.containsKey(name)) {
            try {
                Connection con = connects.get(name);
                Statement stmt2 = con.createStatement();
                ArrayList<MethodTrace> methods = new ArrayList<>();
                ResultSet rs = stmt2.executeQuery("SELECT * FROM point WHERE component LIKE 'mt' OR component LIKE 'j9trc_aux';");
                MethodTrace tempMethodTrace = null;
                int stackLevel = 0;
                while (rs.next()) {
                    String component = rs.getString("component"), type = rs.getString("type");
                    if (component.trim().equalsIgnoreCase("mt")) {
                        String[] parameters = desanitize(rs.getString("parameters")).split("\\|\\|");
                        if (type.trim().equalsIgnoreCase("entry")) {
                            tempMethodTrace = new MethodTrace();
                            if (parameters.length >= 3) {
                                tempMethodTrace.className = parameters[0];
                                tempMethodTrace.methodName = parameters[1];
                                tempMethodTrace.parameters = parameters[2];
                                tempMethodTrace.definition = rs.getString("formatted_parameters").substring(1);
                                tempMethodTrace.timeIn = rs.getLong("t_time");
                                tempMethodTrace.stackTrace = "";
                                tempMethodTrace.tp_ids = new String[]{rs.getString("tp_id"), ""};
                                tempMethodTrace.stack_level = stackLevel;
                                stackLevel++;
                                methods.add(tempMethodTrace);
                            }
                        } else if (type.trim().equalsIgnoreCase("exit")) {
                            MethodTrace tempMethodTrace1 = new MethodTrace();
                            if (parameters.length >= 3) {
                                stackLevel--;
                                tempMethodTrace1.className = parameters[0];
                                tempMethodTrace1.methodName = parameters[1];
                                tempMethodTrace1.parameters = parameters[2];
                                tempMethodTrace1.stack_level = stackLevel;
                                int i = methods.lastIndexOf(tempMethodTrace1);
                                if (i >= 0) {
                                    tempMethodTrace1 = methods.get(i);
                                    tempMethodTrace1.timeOut = rs.getLong("t_time");
                                    tempMethodTrace1.isCompleted = true;
                                    tempMethodTrace1.tp_ids[1] = rs.getString("tp_id");
                                    tempMethodTrace1.hasException = rs.getString("formatted_parameters").startsWith("*");
                                }
                            }
                        }
                    } else if (component.trim().equalsIgnoreCase("j9trc_aux")) {
                        if (tempMethodTrace != null) {
                            tempMethodTrace.stackTrace += rs.getString("formatted_parameters") + "\n";
                        }
                    }
                }
                return methods;
            } catch (SQLException ex) {
                Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    /**
     * Replaces the new-lines with \\n and quotes and multiple whitespaces with a single whitespace.
     * @param what the string to process
     * @return the processed string
     */
    public String sanitize(String what) {
        if (what != null) {
            return what.replaceAll("[\n]", "\\n").replaceAll("[\\'\\\"\\s]+", " ").trim();
        } else {
            return "";
        }
    }

    /**
     * Replaces the \\n with new-lines
     * @param what the string to process
     * @return the processed string
     */
    public String desanitize(String what) {
        if (what != null) {
            return what.replaceAll("\\n", "\n").trim();
        } else {
            return "";
        }
    }

    /**
     * Executes SQL query on the database file corresponding to the trace.
     * @param name the name of the trace
     * @param command the SQL query
     * @return the TableModel containing the results, or null if invalid name or command.
     */
    public DefaultTableModel runSQL(String name, String command) {
        System.out.println(name);
        if (connects.containsKey(name)) {
            try {
                Connection con = connects.get(name);
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(command);
                return buildTableModel(rs);
            } catch (SQLException ex) {
                Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    /**
     * Gets the method trace entries in the corresponding trace
     * @param name the name of the trace
     * @param indent indentation is done with the stack level if true
     * @return a String containing method trace entries, null if name is invalid
     */
    public String getMethodTrace(String name, boolean indent) {
        if (connects.containsKey(name)) {
            try {
                Connection con = connects.get(name);
                Statement stmt2 = con.createStatement();
                ResultSet rs = stmt2.executeQuery("SELECT * FROM point WHERE component LIKE 'mt';");
                StringBuilder trace = new StringBuilder();
                int stackLevel = 0;
                while (rs.next()) {
                    if (indent) {
                        String type = rs.getString("type");
                        if (type.trim().equalsIgnoreCase("entry")) {
                            stackLevel++;
                        }
                        for (int i = 0; i < stackLevel; i++) {
                            trace.append("    ");
                        }
                        if (type.trim().equalsIgnoreCase("exit")) {
                            stackLevel--;
                        }
                    }
                    String sig=rs.getString("formatted_parameters");
                    Matcher match=Pattern.compile("[ ,]").matcher(sig);
                    trace.append(match.find()?sig.substring(0, match.start()):sig).append("\n");
                }
                return trace.toString();
            } catch (SQLException ex) {
                Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    /**
     * Gets the method statistics(invocation count) of the trace(s)
     * @param names the names of the traces
     * @return the TableModel containing all the method entries
     */
    public DefaultTableModel getMethodStats(String... names) {
        ArrayList<String> methods = new ArrayList<>();
        Vector<Vector<String>> data = new Vector();
        Vector<String> columnNames = new Vector<>();
        columnNames.add("Method Name");
        int column = 1;
        for (String name : names) {
            if (connects.containsKey(name)) {
                try {
                    Connection con = connects.get(name);
                    Statement stmt = con.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT parameters,COUNT(parameters) FROM point WHERE component='mt' AND type='Entry' GROUP BY parameters;");
                    columnNames.add(name + " (Invocations count)");
                    while (rs.next()) {
                        String methodsName = rs.getString(1);
                        Vector<String> data_row;
                        if (methods.contains(methodsName)) {
                            data_row = data.get(methods.indexOf(methodsName));
                        } else {
                            data_row = new Vector();
                            data_row.add(methodsName);
                            data.add(data_row);
                            methods.add(methodsName);
                        }
                        while (data_row.size() < column) {
                            data_row.add("0");
                        }
                        data_row.add(rs.getString(2));
                    }

                    column++;

                } catch (SQLException ex) {
                    Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        for (Vector<String> vector : data) {
            while (vector.size() < column) {
                vector.add("0");
            }
        }
        DefaultTableModel model = new DefaultTableModel(data, columnNames);

        return model;

    }

    /**
     * Gets information about a trace.
     * @param name the name of the trace
     * @return the information about the trace
     */
    public String getInfo(String name) {
        System.out.println(name);
        String info = "";
        if (connects.containsKey(name)) {
            try {
                Connection con = connects.get(name);
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM info;");
                while (rs.next()) {
                    info += rs.getString("key").toUpperCase() + "\n";
                    info += desanitize(rs.getString("value")) + "\n________________________________________________________________________________________________\n\n";
                }
            } catch (SQLException ex) {
                Logger.getLogger(TraceManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return info;
    }

    /**
     * Converts ResultSet to a TableModel
     * @param rs the ResultSet
     * @return the TableModel
     * @throws SQLException on error operating with ResultSet
     */
    private DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();

        // names of columns
        Vector<String> columnNames = new Vector<>();
        int columnCount = metaData.getColumnCount();
        columnNames.add("Sl. No.");
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnName(column));
        }

        // data of the table
        DefaultTableModel model = new DefaultTableModel(getData(rs), columnNames);

        return model;
    }

    /**
     * Converts the ResultSet to Vector&lt;Vector&lt;String&gt;&gt;
     * @param rs the ResultSet
     * @return the Vector containing data
     * @throws SQLException on error operating with ResultSet
     */
    public Vector<Vector<Object>> getData(ResultSet rs) throws SQLException {
        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> vector = new Vector<>();
            Integer in=rs.getRow();
            vector.add(in);
            for (int columnIndex = 1; columnIndex <= rs.getMetaData().getColumnCount(); columnIndex++) {
                vector.add(rs.getObject(columnIndex));
            }
            data.add(vector);
        }
        return data;

    }

    /**
     * Separates the items in data to a delimeter separated string
     * @param delimeter the delimiter
     * @param data the items
     * @return the delimeter separated string
     */
    private String spaceSeparate(String delimeter, Object... data) {
        if (data != null && data.length > 0) {
            String str = data[0].toString();
            for (int i = 1; i < data.length; i++) {
                str += delimeter + data[i].toString();
            }
            return str;
        }
        return "";
    }

    /**
     * Separates the items in data to a delimeter separated string
     * @param delimeter the delimiter
     * @param max the maximum number of items to include
     * @param data the items
     * @return the delimeter separated string
     */
    private String spaceSeparate(String delimeter, int max, Object... data) {
        if (data != null && data.length > 0) {
            String str = data[0].toString();
            if (data.length < max) {
                max = data.length;
            }
            for (int i = 1; i < max; i++) {
                str += delimeter + data[i].toString();
            }
            return str;
        }
        return "";
    }

    public static TraceManager traceManager;

    /**
     * 
     * @return The TraceManager instance currently in use.
     */
    public static TraceManager getTraceManager() {
        if (traceManager == null) {
            traceManager = new TraceManager();
        }
        return traceManager;
    }

}
