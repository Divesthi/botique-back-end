package com.dreamworks.bqom.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class DatabaseBackupService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${backup.directory:./backups}")
    private String backupDirectory;

    @Value("${backup.retention.days:30}")
    private int retentionDays;

    @Value("${backup.pgdump.path:pg_dump}")
    private String pgdumpPath;

    @Autowired
    private GoogleDriveService googleDriveService;

    private String dbName;
    private String dbHost;
    private String dbPort;

    @PostConstruct
    public void init() {
        // Parse database connection details from URL
        // Format: jdbc:postgresql://localhost:5432/bqom
        try {
            String urlWithoutPrefix = dbUrl.replace("jdbc:postgresql://", "");
            String[] parts = urlWithoutPrefix.split("/");
            String[] hostPort = parts[0].split(":");
            dbHost = hostPort[0];
            dbPort = hostPort.length > 1 ? hostPort[1] : "5432";
            dbName = parts[1].split("\\?")[0]; // Remove any query parameters

            // Create backup directory if it doesn't exist
            Path backupPath = Paths.get(backupDirectory);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
                log.info("Created backup directory: {}", backupDirectory);
            }

            log.info("Database backup service initialized. Database: {}, Host: {}, Port: {}",
                    dbName, dbHost, dbPort);
            log.info("Backup directory: {}, Retention: {} days", backupDirectory, retentionDays);
        } catch (Exception e) {
            log.error("Failed to initialize database backup service", e);
        }
    }

    /**
     * Scheduled backup task that runs daily at 2:00 AM IST (Indian Standard Time)
     * Cron expression: second minute hour day-of-month month day-of-week
     * 0 0 2 * * * = At 02:00:00 every day
     * Zone set to Asia/Kolkata for IST
     */
    //@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void performScheduledBackup() {
        log.info("Starting scheduled database backup at 2:00 AM IST");
        performBackup();
    }

    /**
     * Performs the database backup using mysqldump
     * @return true if backup was successful, false otherwise
     */
    public boolean performBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = String.format("bqom_backup_%s.sql", timestamp);
        String backupFilePath = Paths.get(backupDirectory, backupFileName).toString();

        try {
            log.info("Creating database backup: {}", backupFilePath);

            // Build pg_dump command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pgdumpPath,
                    "-h", dbHost,
                    "-p", dbPort,
                    "-U", dbUsername,
                    "-F", "p",
                    "--clean",
                    dbName
            );
            processBuilder.environment().put("PGPASSWORD", dbPassword);

            // Redirect output to backup file
            File backupFile = new File(backupFilePath);
            processBuilder.redirectOutput(backupFile);
            processBuilder.redirectErrorStream(false);

            // Execute the backup
            Process process = processBuilder.start();

            // Capture any errors
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                long fileSize = Files.size(Paths.get(backupFilePath));
                log.info("Database backup completed successfully: {} (Size: {} bytes)",
                        backupFilePath, fileSize);

                // Upload backup to Google Drive
                googleDriveService.uploadBackup(backupFile);

                // Clean up old local and Drive backups
                cleanupOldBackups();
                googleDriveService.cleanupOldDriveBackups(retentionDays);
                return true;
            } else {
                log.error("Database backup failed with exit code: {}. Error: {}",
                        exitCode, errorOutput.toString());
                // Delete failed backup file if it exists
                Files.deleteIfExists(Paths.get(backupFilePath));
                return false;
            }

        } catch (Exception e) {
            log.error("Error during database backup", e);
            return false;
        }
    }

    /**
     * Removes backup files older than the retention period
     */
    private void cleanupOldBackups() {
        try {
            Path backupPath = Paths.get(backupDirectory);
            if (!Files.exists(backupPath)) {
                return;
            }

            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

            Files.list(backupPath)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant()
                                    .isBefore(cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.info("Deleted old backup file: {}", path.getFileName());
                        } catch (Exception e) {
                            log.warn("Failed to delete old backup file: {}", path.getFileName(), e);
                        }
                    });

        } catch (Exception e) {
            log.warn("Error during cleanup of old backups", e);
        }
    }

    /**
     * Restores the database from a backup file
     * @param backupFilePath path to the backup file
     * @return true if restore was successful, false otherwise
     */
    public boolean restoreBackup(String backupFilePath) {
        try {
            log.info("Starting database restore from: {}", backupFilePath);

            File backupFile = new File(backupFilePath);
            if (!backupFile.exists()) {
                log.error("Backup file does not exist: {}", backupFilePath);
                return false;
            }

            // Build psql restore command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "psql",
                    "-h", dbHost,
                    "-p", dbPort,
                    "-U", dbUsername,
                    "-d", dbName
            );
            processBuilder.environment().put("PGPASSWORD", dbPassword);

            // Redirect input from backup file
            processBuilder.redirectInput(backupFile);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Database restore completed successfully from: {}", backupFilePath);
                return true;
            } else {
                log.error("Database restore failed with exit code: {}. Output: {}",
                        exitCode, output.toString());
                return false;
            }

        } catch (Exception e) {
            log.error("Error during database restore", e);
            return false;
        }
    }

    /**
     * Lists all available backup files
     * @return array of backup file names
     */
    public String[] listBackups() {
        try {
            Path backupPath = Paths.get(backupDirectory);
            if (!Files.exists(backupPath)) {
                return new String[0];
            }

            return Files.list(backupPath)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .map(path -> path.getFileName().toString())
                    .sorted((a, b) -> b.compareTo(a)) // Sort newest first
                    .toArray(String[]::new);

        } catch (Exception e) {
            log.error("Error listing backup files", e);
            return new String[0];
        }
    }
}
