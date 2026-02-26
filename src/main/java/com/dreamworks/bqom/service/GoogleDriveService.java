package com.dreamworks.bqom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.client.http.FileContent;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class GoogleDriveService {

    @Value("${google.drive.credentials.path}")
    private String credentialsPath;

    @Value("${google.drive.folder.id}")
    private String folderId;

    private Drive driveService;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("BQOM Backup")
                    .build();

            log.info("Google Drive service initialized. Backup folder ID: {}", folderId);
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service. Backups will only be stored locally.", e);
        }
    }

    /**
     * Uploads a file to the configured Google Drive folder.
     * @param file the local file to upload
     * @return the Drive file ID of the uploaded file, or null if upload failed
     */
    public String uploadBackup(java.io.File file) {
        if (driveService == null) {
            log.warn("Google Drive service not available. Skipping upload for: {}", file.getName());
            return null;
        }
        try {
            File fileMetadata = new File();
            fileMetadata.setName(file.getName());
            fileMetadata.setParents(Collections.singletonList(folderId));

            FileContent mediaContent = new FileContent("application/octet-stream", file);

            File uploaded = driveService.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name, size")
                    .execute();

            log.info("Backup uploaded to Google Drive: {} (ID: {}, Size: {} bytes)",
                    uploaded.getName(), uploaded.getId(), uploaded.getSize());
            return uploaded.getId();
        } catch (Exception e) {
            log.error("Failed to upload backup to Google Drive: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Deletes old backup files from Google Drive, keeping only files matching the .sql pattern
     * that are older than the given number of days.
     * @param retentionDays number of days to retain backups
     */
    public void cleanupOldDriveBackups(int retentionDays) {
        if (driveService == null) {
            return;
        }
        try {
            java.time.Instant cutoff = java.time.Instant.now()
                    .minus(retentionDays, java.time.temporal.ChronoUnit.DAYS);

            String query = String.format(
                    "'%s' in parents and name contains 'bqom_backup' and name contains '.sql' and trashed = false",
                    folderId);

            List<File> files = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name, createdTime)")
                    .execute()
                    .getFiles();

            if (files == null || files.isEmpty()) {
                return;
            }

            for (File file : files) {
                java.time.Instant createdTime = java.time.Instant.ofEpochMilli(
                        file.getCreatedTime().getValue());
                if (createdTime.isBefore(cutoff)) {
                    driveService.files().delete(file.getId()).execute();
                    log.info("Deleted old backup from Google Drive: {} (ID: {})",
                            file.getName(), file.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Error during Google Drive backup cleanup", e);
        }
    }
}
