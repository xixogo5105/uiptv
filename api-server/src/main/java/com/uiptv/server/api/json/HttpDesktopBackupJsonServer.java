package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.service.AppDataRefreshService;
import com.uiptv.service.DatabaseBackupArchiveService;
import com.uiptv.service.remotesync.SecureTempFileSupport;
import com.uiptv.util.AppLog;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;

import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopBackupJsonServer implements HttpHandler {
    private static final long MAX_BACKUP_ARCHIVE_BYTES = 256L * 1024 * 1024;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            createBackup(exchange);
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            restoreBackup(exchange);
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,POST");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void createBackup(HttpExchange exchange) throws IOException {
        Path archive = SecureTempFileSupport.createTempFile("uiptv-api-backup-", ".zip");
        try {
            String databasePath = ConfigurationApplicationService.getInstance().getDatabasePath();
            if (databasePath == null || databasePath.isBlank()) {
                writeJsonResponse(exchange, 500, "{\"error\":\"Database path is unavailable\"}");
                return;
            }
            DatabaseBackupArchiveService.getInstance()
                    .createBackupArchive(databasePath, archive.toString());
            long archiveBytes = Files.size(archive);
            if (archiveBytes > MAX_BACKUP_ARCHIVE_BYTES) {
                writeJsonResponse(exchange, 413, "{\"error\":\"Backup archive exceeds the download limit\"}");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", DatabaseBackupArchiveService.MIME_TYPE);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\""
                    + DatabaseBackupArchiveService.defaultFileName(System.currentTimeMillis() / 1000L) + "\"");
            exchange.sendResponseHeaders(200, archiveBytes);
            try (InputStream input = Files.newInputStream(archive);
                 OutputStream output = exchange.getResponseBody()) {
                input.transferTo(output);
            }
        } catch (SQLException | IOException error) {
            AppLog.addErrorLog(HttpDesktopBackupJsonServer.class,
                    "Unable to create database backup (" + error.getClass().getSimpleName() + ")");
            if (exchange.getResponseCode() == -1) {
                writeJsonResponse(exchange, 500, "{\"error\":\"Unable to create database backup\"}");
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private void restoreBackup(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !(contentType.toLowerCase(Locale.ROOT).startsWith("application/zip")
                || contentType.toLowerCase(Locale.ROOT).startsWith("application/octet-stream"))) {
            writeJsonResponse(exchange, 415, "{\"error\":\"A ZIP backup is required\"}");
            return;
        }
        Path archive = SecureTempFileSupport.createTempFile("uiptv-api-restore-", ".zip");
        try {
            long archiveBytes = 0;
            boolean tooLarge = false;
            try (InputStream input = exchange.getRequestBody();
                 OutputStream output = Files.newOutputStream(archive)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    archiveBytes += read;
                    if (archiveBytes > MAX_BACKUP_ARCHIVE_BYTES) {
                        tooLarge = true;
                        break;
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (tooLarge) {
                writeJsonResponse(exchange, 413, "{\"error\":\"Backup archive exceeds the upload limit\"}");
                return;
            }
            byte[] header;
            try (InputStream input = Files.newInputStream(archive)) {
                header = input.readNBytes(4);
            }
            if (header.length < 4 || header[0] != 'P' || header[1] != 'K') {
                writeJsonResponse(exchange, 415, "{\"error\":\"A ZIP backup archive is required\"}");
                return;
            }
            String databasePath = ConfigurationApplicationService.getInstance().getDatabasePath();
            if (databasePath == null || databasePath.isBlank()) {
                writeJsonResponse(exchange, 500, "{\"error\":\"Database path is unavailable\"}");
                return;
            }
            DatabaseBackupArchiveService.BackupArchiveReport report;
            try {
                report = DatabaseBackupArchiveService.getInstance()
                        .restoreBackupArchive(archive.toString(), databasePath);
                AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
            } catch (SQLException | IOException error) {
                AppLog.addErrorLog(HttpDesktopBackupJsonServer.class,
                        "Unable to restore database backup (" + error.getClass().getSimpleName() + ")");
                writeJsonResponse(exchange, 422,
                        "{\"error\":\"Unable to restore the selected database backup\"}");
                return;
            }
            writeJsonResponse(exchange, 200, new JSONObject()
                    .put("databaseBytes", report.databaseBytes())
                    .put("archiveBytes", report.archiveBytes())
                    .toString());
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}