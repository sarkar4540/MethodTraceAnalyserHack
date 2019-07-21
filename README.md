# Introduction

Leverages IBM Java Xtrace options to provide meaningful insights about methods.

# Requirements

Requires IBM Java 8 or highier

# Usage

A demonstration of the application can be seen on [Youtube](https://youtu.be/96nsZEiTkmQ).

## Obtaining Binaries

The Java Binary Archive or *.jar file can be obtained from [here](target/MethodTraceAnalyser-1.0-SNAPSHOT-jar-with-dependencies.jar).

## Starting the application

```bash
java -jar [PATH_TO_JAR_FILE]/MethodTraceAnalyser-1.0-SNAPSHOT-jar-with-dependencies.jar
```

On first start, you must specify the Workspace directory, and the IBM or OpenJ9 JRE Installation Location.

## Using the application

### Adding Trace Files

Trace files can be added by two ways - Import the Trace File and Run with Tracing.

#### Import the Trace Files

The XTrace binary output files can be imported by using `File Menu` -> `Import Trace` option or by using the `Import Trace` button. Then, you must provide a name for the Trace, in which you may specify if the trace file was of passing case or a failing case. Finally, you must select single or multiple subsequent Binary Trace file(s) in the File Chooser popup, corresponding to the trace, to add the Trace.

#### Run with Tracing

Run with Tracing allows a compiled Java Project or a jar file to be executed from within the application. You can start by using `Tools Menu` -> `Run with Tracing` option or by using the `Run with Tracing` button. Then, you must specify the Location of the JAR file or the root directory containing the compiled class files of the Java Project. In case a directory is selected, you must also specify the `main class`. You must also specify the `Methods` which are to be traced in format similar to the methods parameter, accepted while using XTrace and the `Output File` where the binary trace file is to be written. Finally, in the next step, you must confirm to start the execution. While execution, you can interact with the application by using the Terminal displayed. On completion of the execution, the Trace file is automatically imported.

### Selecting Trace for operations

The application shows a list of names of the added trace(s) in the main window. User can specify the trace(s) to be used, for performing operations on, by selecting the respective traces from the list. Double clicking on any trace file shows the information about the respective trace file.

### Operating on trace(s)

The application provides multiple operations to be performed on trace files to visualise and contrast - Run SQL, Compute Difference, Method Tree and Method Stats. 

#### Run SQL

On single selected trace, you can run SQL queries on the data of XTrace. The queries allowed are read-only and must be a SQLITE compatible syntax. You can run query on two tables - `info`, `point` and `thread` - to view data. Table `info` contains information regarding the configurations of the trace. Table `point` contains information regarding the tracepoint invocations during Xtrace. Table `thread` contains information regarding the trace threads which were initiated during XTrace. On clicking on the `Run SQL` button or `Tools` -> `Run SQL` menu entry, you will be asked to enter the query. After that a new tab shall open which will contain a table displaying the result of the query. You can search any column for data by selecting and right-clicking on any cell of the column and entering the search term (RegEx preferred). The next cell containing the match shall be selected, if found.

#### Compute Difference

Any two selected traces can be used to find the difference between them. On clicking on the `Compute Difference` button or `Tools` -> `Compute Difference` menu entry, a new tab shall open which will consist of a table with three columns, two with respective trace names and one `common` in the middle. The columns with trace names will contain the method trace invocations found only in the respective traces, while the column `common` shall contain the invocations found in both.

This operation can be used to **compare the method trace invocations of a passing case and a failing case**.

#### Method Tree

Of a single selected trace, you can find the method tree which shows the **method trace invocations as a tree**. The tree also highlights the methods which are **taking large amount of computation time, which have exceptions and which have incomplete executions**. On clicking on the `Method Tree` button or `Tools` -> `Method Tree` menu entry, a new tab shall open which will consist of a tree with the aforementioned description. On double-clicking on any entry of the tree, the description about the methods are shown along with its stacktrace.

On selection of multiple traces, a comparative view containing trees of all the selected traces is loaded.

#### Method Stats

Of a single selected trace, you can find the method invocation counts, which **shows the frequency of invocations of each methods in tabular, and ajdacently as histogram form**. On clicking on the `Method Stats` button or `Tools` -> `Method Stats` menu entry, a new tab shall open which will consist of a table with the aforementioned description.

On selection of multiple traces, a comparative view containing method invocation counts about all the selected traces is loaded.

## Obtaining Sources

```bash
git clone https://github.com/sarkar4540/MethodTraceAnalyserHack.git
cd MethodTraceAnalyserHack
```

# Documentation

The idea and concepts can be obtained from the [Ideation Document](Idea.pdf), [Presentation](Presentation.pptx) and the method usages can be obtained from the [javadoc](target/site/apidocs) of the project.

# Dependencies

It leverages [IBM Java 8 SE](https://developer.ibm.com/javasdk/documentation/) with the libraries - 
[SQLite DB](https://www.sqlite.org/index.html) and [Google's Diff Match Patch](https://github.com/google/diff-match-patch) - and code from [OpenJ9 Traceformat class](https://github.com/eclipse/openj9/blob/master/jcl/src/openj9.traceformat/share/classes/com/ibm/jvm/traceformat/TraceFormat.java), to perform various operations on the traces. Additionally, it uses Java Swing Framework to implement GUI features.
