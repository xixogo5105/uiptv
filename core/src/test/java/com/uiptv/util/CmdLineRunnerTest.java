package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CmdLineRunnerTest {

    @Test
    void testParseCommand() {
        String cmd = "open -a /Applications/Kodi.app/Contents/MacOS/Kodi --args";

        List<String> cmdArray = CmdLineRunner.parse(cmd);

        assertEquals(4, cmdArray.size());
        assertEquals("open", cmdArray.get(0));
        assertEquals("-a", cmdArray.get(1));
        assertEquals("/Applications/Kodi.app/Contents/MacOS/Kodi", cmdArray.get(2));
        assertEquals("--args", cmdArray.get(3));

    }

    @Test
    void testParseInvalidCommand() {
        String cmd = " ";
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> CmdLineRunner.parse(cmd));
        assertEquals("Command cannot be null or empty", illegalArgumentException.getMessage());
    }

    /**
     * Test for parsing a command with quoted arguments.
     * <p>
     * The program path if it contains spaces should be enclosed in quotes.
     */
    @Test
    void testParseCommandWithQuotedPath() {
        String cmd = "\"C:\\Program Files\\Software\foo.exe\" -arg1 -arg2";

        List<String> cmdArray = CmdLineRunner.parse(cmd);

        assertEquals(3, cmdArray.size());
    }

    @Test
    void createProcessBuilder_discardsChildOutputToAvoidBlockedPlayerPipes() {
        ProcessBuilder builder = CmdLineRunner.createProcessBuilder(
                List.of("player", "http://example.test/stream"),
                Map.of("UIPTV_TEST_ENV", "1")
        );

        assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectError());
        assertEquals("1", builder.environment().get("UIPTV_TEST_ENV"));
    }

    @Test
    void createProcessBuilder_allowsNoisyChildToExitWithoutPipeDrainers() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            Path tempDir = Files.createTempDirectory("uiptv-noisy-child");
            Path source = tempDir.resolve("NoisyChild.java");
            Files.writeString(source, """
                    public class NoisyChild {
                        public static void main(String[] args) {
                            for (int i = 0; i < 20_000; i++) {
                                System.out.println("stdout padding padding padding padding " + i);
                                System.err.println("stderr padding padding padding padding " + i);
                            }
                        }
                    }
                    """);

            String javaExecutable = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
            ).toString();
            Process process = CmdLineRunner.createProcessBuilder(List.of(javaExecutable, source.toString()), Map.of()).start();
            process.getOutputStream().close();

            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Noisy child process should not block on stdout/stderr");
            assertEquals(0, process.exitValue());
        });
    }

}
