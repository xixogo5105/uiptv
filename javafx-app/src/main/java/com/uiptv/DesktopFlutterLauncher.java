package com.uiptv;

import com.uiptv.model.Configuration;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DesktopFlutterLauncher {
    private DesktopFlutterLauncher() {
    }

    public static void launch() throws IOException, InterruptedException {
        Path flutterProject = resolveFlutterProjectDirectory(
                Path.of(System.getProperty("user.dir")),
                System.getenv("UIPTV_FLUTTER_PROJECT")
        );
        initializeI18n(ConfigurationService.getInstance().read());
        UIptvServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(UIptvServer::stop));

        String device = desktopDevice();
        String flutterExecutable = System.getenv().getOrDefault("UIPTV_FLUTTER_CLI", "flutter");
        List<String> command = new ArrayList<>();
        command.add(flutterExecutable);
        command.add("run");
        command.add("-d");
        command.add(device);
        command.add("--dart-define=UIPTV_API_BASE_URL=" + UIptvServer.getDesktopApiLoopbackUrl());
        command.add("--dart-define=UIPTV_API_TOKEN=" + UIptvServer.getDesktopApiToken());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(flutterProject.toFile());
        processBuilder.inheritIO();
        Process flutterProcess = null;
        try {
            flutterProcess = processBuilder.start();
            int exitCode = flutterProcess.waitFor();
            if (exitCode != 0) {
                throw new IOException("Flutter desktop process exited with status " + exitCode);
            }
        } finally {
            if (flutterProcess != null && flutterProcess.isAlive()) {
                flutterProcess.destroy();
                if (!flutterProcess.waitFor(5, TimeUnit.SECONDS)) {
                    flutterProcess.destroyForcibly();
                }
            }
            UIptvServer.stop();
        }
    }

    static void initializeI18n(Configuration configuration) {
        I18n.initialize(configuration == null ? null : configuration.getLanguageLocale());
    }

    static Path resolveFlutterProjectDirectory(Path workingDirectory, String configuredPath) throws IOException {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
            Path project = (configured.isAbsolute() ? configured : workingDirectory.resolve(configured)).normalize();
            if (Files.isRegularFile(project.resolve("pubspec.yaml"))) {
                return project;
            }
            throw new IOException("Flutter project not found at " + project
                    + " (UIPTV_FLUTTER_PROJECT must point to a Flutter project)");
        }

        Path current = workingDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pubspec.yaml"))) {
                return current;
            }
            Path nestedProject = current.resolve("desktop_flutter");
            if (Files.isRegularFile(nestedProject.resolve("pubspec.yaml"))) {
                return nestedProject;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate desktop_flutter/pubspec.yaml. "
                + "Set UIPTV_FLUTTER_PROJECT to the Flutter project directory.");
    }

    private static String desktopDevice() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.startsWith("mac")) {
            return "macos";
        }
        if (osName.startsWith("windows")) {
            return "windows";
        }
        return "linux";
    }
}
