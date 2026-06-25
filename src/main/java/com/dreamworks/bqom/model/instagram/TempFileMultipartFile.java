package com.dreamworks.bqom.model.instagram;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * A disk-persisted implementation of {@link MultipartFile} to preserve uploaded file
 * bytes during asynchronous processing without keeping them in the JVM Heap memory.
 *
 * <p>By persisting the multipart files to disk in the OS temporary directory,
 * we prevent OutOfMemoryError (OOM) under concurrent publishing load while still
 * surviving the request thread termination.
 */
public class TempFileMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final File file;

    public TempFileMultipartFile(String name, String originalFilename, String contentType, File file) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.file = file;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return file == null || !file.exists() || file.length() == 0;
    }

    @Override
    public long getSize() {
        return file != null && file.exists() ? file.length() : 0;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Deletes the underlying temporary file from disk. */
    public void clean() {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
