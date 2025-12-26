package com.boxcopilot.backend.service;

import com.boxcopilot.backend.exception.FileStorageException;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service for handling image storage operations.
 * Images are stored in two versions: thumbnail (200x200px) and large (1024px).
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final int THUMBNAIL_SIZE = 200;
    private static final int LARGE_SIZE = 1024;
    private static final String THUMBNAIL_SUFFIX = "_thumb.jpg";
    private static final String LARGE_SUFFIX = "_large.jpg";
    
    @Value("${app.image.storage.path}")
    private String storagePath;
    
    private Path storageLocation;

    @PostConstruct
    public void init() {
        try {
            this.storageLocation = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(this.storageLocation);
            log.info("Image storage initialized at: {}", this.storageLocation);
        } catch (IOException e) {
            log.error("Failed to create image storage directory", e);
            throw new FileStorageException("Failed to create image storage directory", e);
        }
    }

    /**
     * Saves an uploaded image in two versions: thumbnail (200x200px) and large (1024px).
     *
     * @param itemId The ID of the item
     * @param file The uploaded image file
     * @return The relative path to the stored thumbnail
     */
    public String saveImage(Long itemId, MultipartFile file) {
        log.info("Saving image for item ID: {}", itemId);
        
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot upload empty file");
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new FileStorageException("File must be an image");
        }
        
        try {
            // Save thumbnail (200x200, center-crop)
            String thumbFilename = itemId + THUMBNAIL_SUFFIX;
            Path thumbPath = this.storageLocation.resolve(thumbFilename);
            
            // Prevent path traversal
            if (!thumbPath.normalize().startsWith(this.storageLocation)) {
                throw new FileStorageException("Cannot store file outside storage directory");
            }
            
            ByteArrayOutputStream thumbStream = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toOutputStream(thumbStream);
            
            Files.copy(
                new ByteArrayInputStream(thumbStream.toByteArray()),
                thumbPath,
                StandardCopyOption.REPLACE_EXISTING
            );
            
            // Save large version (1024px max dimension, keep aspect ratio)
            String largeFilename = itemId + LARGE_SUFFIX;
            Path largePath = this.storageLocation.resolve(largeFilename);
            
            if (!largePath.normalize().startsWith(this.storageLocation)) {
                throw new FileStorageException("Cannot store file outside storage directory");
            }
            
            ByteArrayOutputStream largeStream = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(LARGE_SIZE, LARGE_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(0.85)
                    .toOutputStream(largeStream);
            
            Files.copy(
                new ByteArrayInputStream(largeStream.toByteArray()),
                largePath,
                StandardCopyOption.REPLACE_EXISTING
            );
            
            log.info("Images saved successfully: {} and {}", thumbFilename, largeFilename);
            return thumbFilename;
            
        } catch (IOException e) {
            log.error("Failed to save image for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to save image", e);
        }
    }

    /**
     * Retrieves the thumbnail image by item ID.
     *
     * @param itemId The ID of the item
     * @return The thumbnail image file as a Resource
     */
    public Resource getImage(Long itemId) {
        try {
            String filename = itemId + THUMBNAIL_SUFFIX;
            Path filePath = this.storageLocation.resolve(filename).normalize();
            
            // Prevent path traversal
            if (!filePath.startsWith(this.storageLocation)) {
                throw new FileStorageException("Invalid file path");
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                log.debug("Thumbnail retrieved for item ID: {}", itemId);
                return resource;
            } else {
                log.warn("Thumbnail not found for item ID: {}", itemId);
                throw new FileStorageException("Image not found");
            }
        } catch (IOException e) {
            log.error("Failed to retrieve thumbnail for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to retrieve image", e);
        }
    }

    /**
     * Retrieves the large image by item ID.
     *
     * @param itemId The ID of the item
     * @return The large image file as a Resource
     */
    public Resource getLargeImage(Long itemId) {
        try {
            String filename = itemId + LARGE_SUFFIX;
            Path filePath = this.storageLocation.resolve(filename).normalize();
            
            // Prevent path traversal
            if (!filePath.startsWith(this.storageLocation)) {
                throw new FileStorageException("Invalid file path");
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                log.debug("Large image retrieved for item ID: {}", itemId);
                return resource;
            } else {
                log.warn("Large image not found for item ID: {}", itemId);
                throw new FileStorageException("Large image not found");
            }
        } catch (IOException e) {
            log.error("Failed to retrieve large image for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to retrieve large image", e);
        }
    }

    /**
     * Deletes both thumbnail and large image files.
     *
     * @param imagePath The path to the image file (filename)
     */
    public void deleteImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        
        try {
            // Delete thumbnail
            Path filePath = this.storageLocation.resolve(imagePath).normalize();
            
            // Prevent path traversal
            if (!filePath.startsWith(this.storageLocation)) {
                throw new FileStorageException("Invalid file path");
            }
            
            Files.deleteIfExists(filePath);
            log.info("Thumbnail deleted: {}", imagePath);
            
            // Delete large version (derive filename from thumbnail path)
            String largeFilename = imagePath.replace(THUMBNAIL_SUFFIX, LARGE_SUFFIX);
            Path largeFilePath = this.storageLocation.resolve(largeFilename).normalize();
            
            if (largeFilePath.startsWith(this.storageLocation)) {
                Files.deleteIfExists(largeFilePath);
                log.info("Large image deleted: {}", largeFilename);
            }
        } catch (IOException e) {
            log.error("Failed to delete image: {}", imagePath, e);
            throw new FileStorageException("Failed to delete image", e);
        }
    }

    /**
     * Gets the last modified time of an image file for ETag/Cache validation.
     *
     * @param itemId The ID of the item
     * @return Last modified time in milliseconds, or 0 if file doesn't exist
     */
    public long getImageLastModified(Long itemId) {
        try {
            String filename = itemId + THUMBNAIL_SUFFIX;
            Path filePath = this.storageLocation.resolve(filename).normalize();
            
            if (!filePath.startsWith(this.storageLocation)) {
                return 0;
            }
            
            if (Files.exists(filePath)) {
                return Files.getLastModifiedTime(filePath).toMillis();
            }
        } catch (IOException e) {
            log.debug("Failed to get last modified time for item ID: {}", itemId, e);
        }
        return 0;
    }

    /**
     * Gets the last modified time of a large image file.
     *
     * @param itemId The ID of the item
     * @return Last modified time in milliseconds, or 0 if file doesn't exist
     */
    public long getLargeImageLastModified(Long itemId) {
        try {
            String filename = itemId + LARGE_SUFFIX;
            Path filePath = this.storageLocation.resolve(filename).normalize();
            
            if (!filePath.startsWith(this.storageLocation)) {
                return 0;
            }
            
            if (Files.exists(filePath)) {
                return Files.getLastModifiedTime(filePath).toMillis();
            }
        } catch (IOException e) {
            log.debug("Failed to get last modified time for large image, item ID: {}", itemId, e);
        }
        return 0;
    }
}
