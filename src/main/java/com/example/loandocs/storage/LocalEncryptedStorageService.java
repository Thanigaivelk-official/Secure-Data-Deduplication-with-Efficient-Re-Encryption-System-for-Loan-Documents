package com.example.loandocs.storage;

import com.example.loandocs.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class LocalEncryptedStorageService implements StorageService {

    private final Path storageDirectory;

    public LocalEncryptedStorageService(@Value("${app.storage.local.base-dir:./storage/encrypted}") String storageDir) {
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            throw new StorageException("Could not initialize encrypted storage directory: " + storageDirectory, e);
        }
    }

    @Override
    public String store(String storagePath, byte[] encryptedData) {
        try {
            Path targetFile = storageDirectory.resolve(storagePath).normalize();
            // Guard against path traversal attacks
            if (!targetFile.startsWith(storageDirectory)) {
                throw new StorageException("Path traversal attempt detected");
            }
            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, encryptedData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return storagePath;
        } catch (IOException e) {
            throw new StorageException("Failed to persist encrypted document to disk", e);
        }
    }

    @Override
    public byte[] retrieve(String storagePath) {
        try {
            Path targetFile = storageDirectory.resolve(storagePath).normalize();
            if (!targetFile.startsWith(storageDirectory) || !Files.exists(targetFile)) {
                throw new StorageException("Encrypted storage object does not exist on disk: " + storagePath);
            }
            return Files.readAllBytes(targetFile);
        } catch (IOException e) {
            throw new StorageException("Failed to read encrypted file from disk", e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        Path targetFile = storageDirectory.resolve(storagePath).normalize();
        return Files.exists(targetFile);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path targetFile = storageDirectory.resolve(storagePath).normalize();
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to delete encrypted file", e);
        }
    }
}
