package com.malinskiy.marathon.vendor.junit4.runner;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

public class Runner {
    public static void main(String[] args) throws Exception {
        Map<String, String> environ = System.getenv();
        File filterFile = new File(environ.get("FILTER"));
        String outputSocket = environ.get("OUTPUT");
        Integer port = getInteger(outputSocket);

        try (Socket socket = new Socket(InetAddress.getLocalHost(), port)) {
            ListenerAdapter adapter = new ListenerAdapter(socket);

            List<String> tests = new ArrayList<>();
            try (Scanner scanner = new Scanner(filterFile)) {
                while (scanner.hasNextLine()) {
                    tests.add(scanner.nextLine());
                }
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Set<Class<?>> klasses = new HashSet<>();
            Set<Description> testDescriptions = new HashSet<>();

            for (String fqtn : tests) {
                String klass = fqtn.substring(0, fqtn.indexOf('#'));
                Class<?> testClass = null;
                try {
                    testClass = Class.forName(klass);
                }
                catch (ClassNotFoundException e) {
                    failVerbously(socket, e);
                }
                klasses.add(testClass);

                String method = fqtn.substring(fqtn.indexOf('#') + 1);
                testDescriptions.add(Description.createTestDescription(testClass, method));
            }

            Map<Description, String> actualClassLocator = new HashMap<>();
            TestFilter testFilter = new TestFilter(testDescriptions, actualClassLocator);

            Request request = Request.classes(klasses.toArray(new Class<?>[] {}))
                .filterWith(testFilter);

            JUnitCore core = new JUnitCore();
            try {
                core.addListener(adapter);
                core.run(request);
                core.removeListener(adapter);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        catch (Exception e) {
            failMiserably(e);
        }
    }

    private static Integer getInteger(String outputSocket) throws Exception {
        Integer port = 0;
        try {
            port = Integer.valueOf(outputSocket);
        }
        catch (NumberFormatException e) {
            failMiserably(e);
        }
        return port;
    }

    private static void failMiserably(Exception e) throws Exception {
        e.printStackTrace();
        throw e;
    }

    private static void failVerbously(Socket socket, Exception e) throws Exception {
        failMiserably(e);
    }
}
