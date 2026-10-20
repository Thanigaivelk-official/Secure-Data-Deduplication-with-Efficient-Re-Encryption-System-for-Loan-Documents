package com.example.loandocs.storage;

import java.io.InputStream;

public interface StorageService {

    /**
     * Persists encrypted bytes under a secure opaque storage identifier.
     * @param storagePath Target relative or absolute storage path
     * @param encryptedData Encrypted ciphertext
     * @return storagePath or resolved storage URI
     */
    String store(String storagePath, byte[] encryptedData);

    /**
     * Retrieves stored encrypted ciphertext.
     */
    byte[] retrieve(String storagePath);

    /**
     * Checks if a storage object physically exists.
     */
    boolean exists(String storagePath);

    /**
     * Deletes a physical storage object.
     */
    void delete(String storagePath);
}
