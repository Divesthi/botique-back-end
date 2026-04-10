package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.repository.enums.UserRole;
import com.dreamworks.bqom.security.RequireRole;
import com.dreamworks.bqom.service.DatabaseBackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(path = "/v1/bqom/backup", produces = "application/json")
@CrossOrigin(origins = "*")
@RequireRole(UserRole.TENANT_ADMIN)
@Slf4j
public class BackupController {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @Value("${backup.directory:./backups}")
    private String backupDirectory;

    /**
     * Trigger a manual database backup
     */
    @PostMapping("/create")
    @CrossOrigin
    public ResponseEntity<Map<String, Object>> createBackup() {
        log.info("Manual backup triggered via API");
        Map<String, Object> response = new HashMap<>();

        boolean success = databaseBackupService.performBackup();

        if (success) {
            response.put("success", true);
            response.put("message", "Database backup created successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.put("success", false);
            response.put("message", "Database backup failed. Check server logs for details.");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * List all available backup files
     */
    @GetMapping("/list")
    @CrossOrigin
    public ResponseEntity<Map<String, Object>> listBackups() {
        Map<String, Object> response = new HashMap<>();

        String[] backups = databaseBackupService.listBackups();
        response.put("success", true);
        response.put("backups", backups);
        response.put("count", backups.length);
        response.put("backupDirectory", backupDirectory);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Restore database from a specific backup file
     * 
     * @param fileName name of the backup file to restore
     */
    @PostMapping("/restore/{fileName}")
    @CrossOrigin
    public ResponseEntity<Map<String, Object>> restoreBackup(@PathVariable("fileName") String fileName) {
        log.info("Database restore triggered via API for file: {}", fileName);
        Map<String, Object> response = new HashMap<>();

        // Validate file name to prevent path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            response.put("success", false);
            response.put("message", "Invalid file name");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        String backupFilePath = Paths.get(backupDirectory, fileName).toString();
        boolean success = databaseBackupService.restoreBackup(backupFilePath);

        if (success) {
            response.put("success", true);
            response.put("message", "Database restored successfully from: " + fileName);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.put("success", false);
            response.put("message", "Database restore failed. Check server logs for details.");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get backup status and configuration
     */
    @GetMapping("/status")
    @CrossOrigin
    public ResponseEntity<Map<String, Object>> getBackupStatus() {
        Map<String, Object> response = new HashMap<>();

        String[] backups = databaseBackupService.listBackups();

        response.put("success", true);
        response.put("backupDirectory", backupDirectory);
        response.put("totalBackups", backups.length);
        response.put("latestBackup", backups.length > 0 ? backups[0] : null);
        response.put("scheduledTime", "02:00 AM IST (Daily)");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
