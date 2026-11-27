package com.uiptv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesktopFlutterLauncherTest {
    @TempDir
    Path tempDirectory;

    @Test
    void locatesFlutterProjectFromRepositoryRoot() throws IOException {
        Path project = Files.createDirectories(tempDirectory.resolve("desktop_flutter"));
        Files.createFile(project.resolve("pubspec.yaml"));

        assertEquals(project, DesktopFlutterLauncher.resolveFlutterProjectDirectory(tempDirectory, null));
    }

    @Test
    void acceptsExplicitFlutterProjectPath() throws IOException {
        Path project = Files.createDirectories(tempDirectory.resolve("flutter-project"));
        Files.createFile(project.resolve("pubspec.yaml"));

        assertEquals(
                project,
                DesktopFlutterLauncher.resolveFlutterProjectDirectory(tempDirectory, "flutter-project")
        );
    }

    @Test
    void reportsMissingFlutterProject() {
        IOException error = assertThrows(
                IOException.class,
                () -> DesktopFlutterLauncher.resolveFlutterProjectDirectory(tempDirectory, "missing")
        );

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("UIPTV_FLUTTER_PROJECT"));
    }
}
