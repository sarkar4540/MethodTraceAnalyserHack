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
 * 
 * The following code contains modified parts from 
 * com.ibm.jvm.traceformat.TraceFormat.java class obtained from OpenJ9.
 */
package com.altcodelabs.methodtrace.analyser;

import com.ibm.jvm.trace.format.api.TraceContext;
import com.ibm.jvm.trace.format.api.TraceThread;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contains the methods to deal with the binary output trace file.
 * @author ani
 */
public class TraceContextFactory {

    private final List inputs, messageFiles, threads;
    
    private final int timezone;
    
    private TraceContext context;
        long recordsInData = 0;
        long totalBytes = 0;

    /**
     * Creates an instance of this class.
     * @param input the list of input files
     */
    public TraceContextFactory(List<File> input) {
        inputs = input;
        messageFiles = MessageFile.getDefault().getValue();
        threads = Threads.getDefault().getValue();
        timezone=Timezone.getDefault().getValue();
    }
    
    /**
     * @return the trace context of the loaded record.
     */
    public TraceContext getContext(){
        return context;
    }
    
    /**
     * @return the number of records processed
     */
    public long getRecordsCount(){
        return recordsInData;
    }
    
    /**
     * 
     * @return the total number of bytes processed
     */
    public long getTotalBytes(){
        return totalBytes;
    }
    
    /**
     * 
     * @return processes all the trace files passed in the constructor
     */
    public boolean loadFile() {
        ArrayList inputFiles = new ArrayList();
        for (Object file : inputs) {
            try {
                inputFiles.add(new RandomAccessFile((File) file, "r"));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(TraceContextFactory.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
        int blockSize = 4000;
        PrintStream error = System.err;
        PrintStream warning = System.out;
        PrintStream debug = System.out;

        try {
            while (true) {
                try {
                    ByteBuffer data;
                    data = ((RandomAccessFile) inputFiles.get(0)).getChannel().map(FileChannel.MapMode.READ_ONLY, 0, blockSize);
                    context = TraceContext.getContext(data, (File) messageFiles.get(0), System.out, warning, error, debug);
                    break;
                } catch (BufferUnderflowException e) {
                    blockSize *= 2;
                }
            }
        } catch (IOException e) {
            /* could be many things, one is a file shorter than blockSize so try a different method */
            try {
                long length = ((RandomAccessFile) inputFiles.get(0)).length();
                if (length > 0) {
                    byte header[] = new byte[(int) length];
                    ((RandomAccessFile) inputFiles.get(0)).seek(0);
                    int i = ((RandomAccessFile) inputFiles.get(0)).read(header);
                    if (i == length && length < blockSize) {
                        context = TraceContext.getContext(header, header.length, (File) messageFiles.get(0), System.out, warning, error, debug);
                    } else {
                        throw new Exception("received premature end of file: " + e.getMessage());
                    }
                } else {
                    throw new Exception("empty trace file");
                }
            } catch (Exception f) {
                /* this wasn't due to filesize < blocksize so print the exception message and exit */
                System.err.println("Unable to read trace header from file: " + f.getMessage());
                System.err.println("Please check that the input file is a binary trace file");
                return false;
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Problem reading the trace file header: " + e.getMessage());
            System.err.println("Please check that that the input file is a binary trace file");
            return false;
        }

        context.setDebugLevel(0);

        /* set up the thread filters */
        Iterator itr = threads.iterator();
        while (itr.hasNext()) {
            Long id = (Long) itr.next();
            context.addThreadToFilter(id);
        }

        context.setTimeZoneOffset(timezone);

        /* add any remaining dat files */
        for (int i = 1; i < messageFiles.size(); i++) {
            File file = (File) messageFiles.get(i);
            try {
                context.addMessageData(file);
            } catch (IOException e) {
                // Problem reading one of the trace format .dat files, issue message and exit
                System.err.println("Unable to process trace format data file: " + file.getAbsolutePath() + " (" + e.getMessage() + ")");
                return false;
            }
        }

        Map indentMap = new HashMap();

        /* read in the blocks from the various files and sort them */

        /* loop over the generational files and add the blocks to the context */
        for (int i = 0; i < inputFiles.size(); i++) {
            try {
                long offset = context.getHeaderSize();
                long recordSize = context.getRecordSize();
                RandomAccessFile traceFile = (RandomAccessFile) inputFiles.get(i);
                
                long length = traceFile.length();
                if ((length - context.getHeaderSize()) % recordSize != 0) {
                    context.warning(context, "The body of the trace file is not a multiple of the record size, file either truncated or corrupt");
                }
                
                while (offset < length) {
                    try {
                        TraceThread thread = context.addData(traceFile, offset);
                        indentMap.put(thread, "");
                    } catch (IllegalArgumentException e) {
                        context.error(context, "Bad block of trace data in input file at offset " + offset + ": " + e.getMessage());
                    }
                    
                    offset += recordSize;
                    totalBytes += recordSize;
                    recordsInData++;
                }
                
            } catch (IOException ex) {
                Logger.getLogger(TraceContextFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }


        return true;
    }

    public static class Timezone {

        Integer timezone;

        public Integer getValue() {
            return timezone;
        }

        public void setValue(int hours, int minutes, boolean signPlus) throws IllegalArgumentException {
            // Validate and convert input time zone string from +|-HH:MM format to +/- integer minutes
            try {
                if ((hours > 12) || (minutes > 60)) {
                    throw new NumberFormatException();
                }
                timezone = (hours * 60) + minutes;
                if (!signPlus) {
                    timezone = -timezone;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The specified timezone offset is not valid. Format is +|-HH:MM");
            }
        }

        public static Timezone getDefault() {
            /* default value is false */
            Timezone tz = new Timezone();
            tz.timezone = 0;
            return tz;
        }
    }

    public static class Threads {

        List threads = new LinkedList();

        public List getValue() {
            return threads;
        }

        public void setValue(String value) throws IllegalArgumentException {
            /* This could be just the one file or could be a comma separated list */
            StringTokenizer st = new StringTokenizer(value, ",");
            String token = "";
            try {
                while (st.hasMoreTokens()) {
                    token = st.nextToken();
                    Long id;

                    /* construct Long's from the id's */
                    if (token.startsWith("0x")) {
                        id = Long.valueOf(token.substring(2), 16);
                    } else {
                        id = Long.valueOf(token);
                    }
                    threads.add(id);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The specified thread id, \"" + token + "\" is not valid. Id's must be a number");
            }
        }

        public static Threads getDefault() {
            return new Threads();
        }
    }

    public static class MessageFile {

        private final List messageFiles = new LinkedList();

        public static MessageFile getDefault() {
            MessageFile messageFile = new MessageFile();
            String dir = Preferences.getPreferences().config.get("jre");
            dir = dir.concat(File.separator).concat("lib").concat(File.separator);

            messageFile.setValue(dir + "J9TraceFormat.dat");
            messageFile.setValue(dir + "OMRTraceFormat.dat");
            try {
                messageFile.setValue(dir + "TraceFormat.dat");
            } catch (IllegalArgumentException e) {
                System.out.println("Warning: " + e.getMessage());
            }
            return messageFile;
        }

        public List getValue() {
            return messageFiles;
        }

        public void setValue(String value) throws IllegalArgumentException {
            /* This could be just the one file or could be a comma separated list */
            StringTokenizer st = new StringTokenizer(value, ",");
            String token = "";
            try {
                while (st.hasMoreTokens()) {
                    token = st.nextToken();
                    /* construct files from the tokens. These files must exist */
                    File datFile = new File(token);
                    if (!datFile.exists()) {
                        throw new IllegalArgumentException("dat file \"" + token + "\" not found");
                    }
                    messageFiles.add(datFile);
                }
            } catch (SecurityException e) {
                throw new IllegalArgumentException("The application does not have permission to access the specified dat file, \"" + token + "\"");
            }
        }

    }
}
