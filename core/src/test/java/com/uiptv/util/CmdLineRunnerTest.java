package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}