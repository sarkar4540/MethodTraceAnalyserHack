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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contains the methods to allow editing and viewing preferences of the application.
 * @author ani
 */
public class Preferences {

    public final HashMap<String, String> config; //The preferences
    File configFile;

    /**
     * creates new instance and sets the default preferences file.
     */
    private Preferences() {
        this.config = new HashMap();
        configFile = new File(System.getProperty("user.home") + File.separator + ".mtrace.conf");
    }

    /**
     * Saves the preferences to the file
     * @return true if successful
     */
    public boolean savePreferences() {
        try {
            if (configFile.exists()) {
                configFile.delete();
            }
            String configStr = "";
            configStr = config.keySet().stream().map((key) -> key + ":" + config.get(key) + "\n").reduce(configStr, String::concat);
            try (FileWriter fwriter = new FileWriter(configFile)) {
                fwriter.write(configStr.trim());
            }
            return true;

        } catch (IOException ex) {
            Logger.getLogger(Preferences.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    /**
     * Loads the preferences from the file
     * @return true if successful
     */
    public boolean loadPreferences() {
        try {
            if (configFile.exists() && configFile.canWrite()) {
                FileReader freader = new FileReader(configFile);
                String configStr = "";
                System.out.println(configStr);
                int b;
                while ((b = freader.read()) > -1) {
                    configStr += (char) b;
                }
                
                if (!configStr.isEmpty()) {
                    String configl[] = configStr.trim().split("\n");
                    for (String line : configl) {
                        String[] lineArr = line.split(":");
                        if (lineArr.length == 2) {
                            config.put(lineArr[0], lineArr[1]);
                        }
                    }
                    return true;
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Preferences.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;

    }
    private static final Preferences PREF = new Preferences();

    /**
     * 
     * @return The current preferences of the application.
     */
    public static Preferences getPreferences() {
        if (PREF == null) {
            return PREF;
        } else {
            return PREF;
        }
    }
}
