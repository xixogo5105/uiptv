package com.uiptv.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to run command line commands.
 */
public final class CmdLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CmdLineRunner.class);

    private static final Pattern CMD_PATTERN = Pattern.compile("\"([^\"]*)\"|\\S+");

    public static class CmdLineException extends Exception {

        public CmdLineException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public void exec(String cmd) throws CmdLineException {
        this.exec(CmdLineRunner.parse(cmd), Collections.emptyMap());
    }

    public void exec(String cmd, Map<String, String> env) throws CmdLineException {
        this.exec(CmdLineRunner.parse(cmd), env);
    }

    public void exec(List<String> cmd, Map<String, String> env) throws CmdLineException {
        try {
            ProcessBuilder processBuilder = createProcessBuilder(cmd, env);
            log.debug("Executing command: {} with ENV={}", cmd, processBuilder.environment());
            Process process = processBuilder.start();
            closeChildInput(process);
        } catch (Exception e) {
            throw new CmdLineException("error executing command \"" + cmd + "\"", e);
        }
    }

    static ProcessBuilder createProcessBuilder(List<String> cmd, Map<String, String> env) {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        if (env != null) {
            processBuilder.environment().putAll(env);
        }
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return processBuilder;
    }

    private static void closeChildInput(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            log.debug("Unable to close child process stdin", e);
        }
    }

    static List<String> parse(String cmd) {
        if (StringUtils.isBlank(cmd)) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = CMD_PATTERN.matcher(cmd);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                result.add(matcher.group(1)); // Quoted argument
            } else {
                result.add(matcher.group()); // Unquoted argument
            }
        }
        return result;
    }

}
