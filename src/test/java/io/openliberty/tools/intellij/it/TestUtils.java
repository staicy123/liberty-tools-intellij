package io.openliberty.tools.intellij.it;

import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


/**
 * Test utilities.
 */
public class TestUtils {

    /**
     * WLP messages.log path.
     */
    public static final Path WLP_MSGLOG_PATH = Paths.get("wlp", "usr", "servers", "defaultServer", "logs", "messages.log");

    /**
     * Liberty server stopped message:
     * CWWKE0036I: The server defaultServer stopped after 12.25 seconds.
     */
    public static final String SEVER_STOPPED_MSG = "CWWKE0036I";

    enum TraceSevLevel {
        INFO, ERROR
    }

    /**
     * Validates that the Liberty server is no longer running.
     *
     * @param wlpInstallPath The liberty installation relative path.
     */
    public static void validateLibertyServerStopped(String testName, String wlpInstallPath) {
        printTrace(TraceSevLevel.INFO, testName + ":validateLibertyServerStopped: Entry.");

        String wlpMsgLogPath = Paths.get(wlpInstallPath, WLP_MSGLOG_PATH.toString()).toString();
        int maxAttempts = 40;
        int retryIntervalSecs = 5;
        boolean foundStoppedMsg = false;

        // Find the server stopped message.
        for (int retryCount = 0; retryCount < maxAttempts; retryCount++) {
            try (BufferedReader br = new BufferedReader(new FileReader(wlpMsgLogPath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(SEVER_STOPPED_MSG)) {
                        foundStoppedMsg = true;
                        break;
                    }
                }

                if (foundStoppedMsg) {
                    break;
                } else {
                    Thread.sleep(retryIntervalSecs * 1000);
                }
            } catch (FileNotFoundException fnfe) {
                fnfe.printStackTrace();
                Assertions.fail("File: " + wlpMsgLogPath + ", could not be found.");

            } catch (Exception e) {
                e.printStackTrace();

                try {
                    Thread.sleep(retryIntervalSecs * 1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        if (!foundStoppedMsg) {
            // If we are here, the expected outcome was not found. Print the Liberty server's messages.log and fail.
            String msgHeader = "TESTCASE: " + testName;
            printLibertyMessagesLogFile(msgHeader, wlpMsgLogPath);
            String msg = testName + ":validateLibertyServerStopped: Exit. Timed out waiting for message " + SEVER_STOPPED_MSG + " in log:" + wlpMsgLogPath;
            printTrace(TraceSevLevel.ERROR, msg);
            Assertions.fail(msg);
        } else {
            printTrace(TraceSevLevel.INFO, testName + ":validateLibertyServerStopped: Exit. The server stopped Successfully.");
        }
    }

    /**
     * Validates that the project is started.
     *
     * @param testName         The name of the test calling this method.
     * @param resourceURI      The project resource URI.
     * @param port             The port number to reach the project
     * @param expectedResponse The expected resource response payload.
     * @param wlpInstallPath   The liberty installation relative path.
     */
    public static void validateProjectStarted(String testName, String resourceURI, int port, String expectedResponse, String wlpInstallPath, boolean findConn) {
        printTrace(TraceSevLevel.INFO, testName + ":validateProjectStarted: Entry. Port: " + port + ", resourceURI: " + resourceURI);

        int retryCountLimit = 75;
        int retryIntervalSecs = 5;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;

            HttpURLConnection conn;
            if (findConn) {
                conn = findHttpConnection(port, resourceURI);
            } else {
                conn = getHttpConnection(port, resourceURI);
            }

            if (conn == null) {
                TestUtils.sleepAndIgnoreException(retryIntervalSecs);
                continue;
            }

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String responseLine;
                StringBuilder content = new StringBuilder();
                while ((responseLine = br.readLine()) != null) {
                    content.append(responseLine).append(System.lineSeparator());
                }

                if (!(content.toString().contains(expectedResponse))) {
                    Thread.sleep(retryIntervalSecs * 1000);
                    conn.disconnect();
                    continue;

                }
                printTrace(TraceSevLevel.INFO, testName + ":validateProjectStarted. Exit. The project started successfully.");

                return;
            } catch (Exception e) {
                TestUtils.sleepAndIgnoreException(retryIntervalSecs);
            }
        }

        // If we are here, the expected outcome was not found. Print the Liberty server's messages.log and fail.
        String msg = testName + ":validateProjectStarted: Timed out while waiting for project with resource URI " + resourceURI + "and port " + port + " to become available.";
        printTrace(TraceSevLevel.ERROR, msg);
        String wlpMsgLogPath = Paths.get(wlpInstallPath, WLP_MSGLOG_PATH.toString()).toString();
        String msgHeader = "Message log for failed test: " + testName + ":validateProjectStarted";
        printLibertyMessagesLogFile(msgHeader, wlpMsgLogPath);
        Assertions.fail(msg);
    }

    /**
     * Finds an active connection object.
     * This is done based on how the LMP/LGP finds a usable port when starting dev mode in a container.
     * If the specified port is not usable, the LMP/LGP increases the specified port number
     * by one until it finds a usable port, which, in turn, it is used to start dev mode in a container.
     * A port may not be usable if the socket associated with the specified port
     * can not yet be bound to between tests.
     *
     * @param port        The initial port number.
     * @param resourceURI The resource URI.
     * @return An active connection object..
     */
    public static HttpURLConnection findHttpConnection(int port, String resourceURI) {
        int testPort = port;
        int maxPortIncrement = 4;
        HttpURLConnection connection = null;
        for (int i = 0; i < maxPortIncrement; i++) {
            connection = getHttpConnection(testPort, resourceURI);
            if (connection != null) {
                break;
            }
            testPort += 1;
        }

        return connection;
    }

    /**
     * Returns an active connection object.
     *
     * @param port        The initial port number.
     * @param resourceURI The resource URI.
     * @return An active connection object.
     */
    public static HttpURLConnection getHttpConnection(int port, String resourceURI) {
        String resourceURL = "http://localhost:" + port + "/" + resourceURI;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(resourceURL);
            HttpURLConnection tmpConn = (HttpURLConnection) url.openConnection();
            tmpConn.setRequestMethod("GET");
            tmpConn.connect();
            int status = tmpConn.getResponseCode();

            if (status == HttpURLConnection.HTTP_OK) {
                conn = tmpConn;
            } else {
                tmpConn.disconnect();
            }
        } catch (Exception e) {
            // Ignore.
        }

        return conn;
    }

    /**
     * Validates the project stopped.
     *
     * @param testName       The name of the test calling this method.
     * @param projUrl        The project's URL.
     * @param wlpInstallPath The liberty installation relative path.
     */
    public static void validateProjectStopped(String testName, String projUrl, String wlpInstallPath) {
        printTrace(TraceSevLevel.INFO, testName + ":validateProjectStopped: Entry. URL: " + projUrl);

        int retryCountLimit = 60;
        int retryIntervalSecs = 2;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;
            int status;
            try {
                URL url = new URL(projUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.connect();
                status = con.getResponseCode();

                if (status == HttpURLConnection.HTTP_OK) {
                    Thread.sleep(retryIntervalSecs * 1000);
                    con.disconnect();
                    continue;
                }

                printTrace(TraceSevLevel.INFO, testName + ":validateProjectStopped. Exit. The project stopped successfully.");
                return;
            } catch (Exception e) {
                try {
                    Thread.sleep(retryIntervalSecs * 1000);
                } catch (Exception ee) {
                    ee.printStackTrace(System.out);
                }
            }
        }

        // If we are here, the expected outcome was not found. Print the Liberty server's messages.log and fail.
        String msg = testName + ":validateProjectStopped: Timed out while waiting for project under URL: " + projUrl + " to stop.";
        printTrace(TraceSevLevel.ERROR, msg);
        String wlpMsgLogPath = Paths.get(wlpInstallPath, WLP_MSGLOG_PATH.toString()).toString();
        String msgHeader = "Message log for failed test: " + testName + ":validateProjectStopped";
        printLibertyMessagesLogFile(msgHeader, wlpMsgLogPath);
        Assertions.fail(msg);
    }

    /**
     * Validates the expected hover string message was raised in popup.
     *
     * @param expectedHoverText The full string of popup data that is expected to be found.
     * @param hoverPopupText    The string found in the popup window
     */
    public static void validateHoverData(String expectedHoverText, String hoverPopupText) {

        if (hoverPopupText.contains(expectedHoverText)) {
            Assertions.assertTrue(hoverPopupText.contains(expectedHoverText));
        } else {
            Assertions.fail("Did not find diagnostic help text expected. Looking for " + expectedHoverText);
        }
    }

    public static void validateStanzaInServerXML(String pathToServerXml, String insertedStanza) {

        try {
            Assertions.assertTrue(isTextInFile(pathToServerXml, insertedStanza));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prints the Liberty server's messages.log identified by the input path.
     *
     * @param wlpMsgLogPath The messages.log path to print.
     */
    public static void printLibertyMessagesLogFile(String msgHeader, String wlpMsgLogPath) {
        System.out.println("--------------------------- messages.log ----------------------------");
        System.out.println(msgHeader);
        System.out.println("---------------------------------------------------------------------");

        try (BufferedReader br = new BufferedReader(new FileReader(wlpMsgLogPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (FileNotFoundException fnfe) {
            System.out.println("File: " + wlpMsgLogPath + " was not found.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("---------------------------------------------------------------------");
    }

    /**
     * Validates that the test report represented by the input path exists.
     *
     * @param pathToTestReport The path to the report.
     */
    public static void validateTestReportExists(Path pathToTestReport) {
        int retryCountLimit = 100;
        int retryIntervalSecs = 1;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;

            boolean fileExists = fileExists(pathToTestReport.toAbsolutePath());
            if (!fileExists) {
                try {
                    Thread.sleep(retryIntervalSecs * 1000);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    continue;
                }
                continue;
            }

            return;
        }
    }

    /**
     * Returns true or false depending on if the input text is found in the target file
     *
     * @throws IOException if an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read
     */
    public static boolean isTextInFile(String filePath, String text) throws IOException {

        List<String> lines = Files.readAllLines(Paths.get(filePath));
        for (String line : lines) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the current process is running on a Windows environment. False, otherwise.
     *
     * @return True if the current process is running on a Windows environment. False, otherwise.
     */
    public static boolean onWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    /**
     * Returns true if the file identified by the input path exists. False, otherwise.
     *
     * @param filePath The file's path.
     * @return True if the file identified by the input path exists. False, otherwise.
     */
    public static boolean fileExists(Path filePath) {
        File f = new File(filePath.toString());
        return f.exists();
    }

    /**
     * Deletes file identified by the input path. If the file is a directory, it must be empty.
     *
     * @param file The file.
     * @return Returns true if the file identified by the input path was deleted. False, otherwise.
     */
    public static boolean deleteFile(File file) {
        boolean deleted = true;

        if (file.exists()) {
            if (!file.isDirectory()) {
                deleted = file.delete();
            } else {
                deleted = deleteDirectory(file);
            }
        }

        return deleted;
    }

    /**
     * Recursively deletes the input file directory.
     *
     * @param file The directory.
     * @return Returns true if the directory identified by the input path was deleted. False, otherwise.
     */
    private static boolean deleteDirectory(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File value : files) {
                deleteDirectory(value);
            }
        }
        return file.delete();
    }

    /**
     * Prints a formatted message to STDOUT.
     *
     * @param traceSevLevel The severity level
     * @param msg           The message to print.
     */
    public static void printTrace(TraceSevLevel traceSevLevel, String msg) {
        switch (traceSevLevel) {
            case INFO -> System.out.println("INFO: " + java.time.LocalDateTime.now() + ": " + msg);
            case ERROR -> System.out.println("ERROR: " + java.time.LocalDateTime.now() + ": " + msg);
            default -> {
            }
        }
    }

    /**
     * Calls Thread.sleep() and ignores any exceptions.
     *
     * @param seconds The amount of seconds to sleep.
     */
    public static void sleepAndIgnoreException(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Determines if the Liberty server should be stopped or not.
     *
     * @param wlpInstallPath The path to the Liberty installation.
     * @return True if the Liberty server should be stopped. False, otherwise.
     */
    public static boolean isServerStopNeeded(String wlpInstallPath) {
        boolean stopServer = false;
        Path msgLogPath = Paths.get(wlpInstallPath, WLP_MSGLOG_PATH.toString());
        if (fileExists(msgLogPath)) {
            try {
                // The file maybe an old log. For now, check for the message indicating
                // that the server is stopped.
                if (!(isTextInFile(msgLogPath.toString(), SEVER_STOPPED_MSG))) {
                    stopServer = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return stopServer;
    }
}
