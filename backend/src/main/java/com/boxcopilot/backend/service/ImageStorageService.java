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
 * Images are stored as WebP thumbnails (200x200px) only.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final int THUMBNAIL_SIZE = 200;
    private static final String THUMBNAIL_SUFFIX = "_thumb.webp";
    
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
     * Saves an uploaded image as a WebP thumbnail.
     * Only the 200x200px thumbnail is stored; the original is discarded.
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
            // Generate filename
            String filename = itemId + THUMBNAIL_SUFFIX;
            Path targetPath = this.storageLocation.resolve(filename);
            
            // Prevent path traversal
            if (!targetPath.normalize().startsWith(this.storageLocation)) {
                throw new FileStorageException("Cannot store file outside storage directory");
            }
            
            // Create thumbnail as WebP (200x200, center-crop)
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat("webp")
                    .toOutputStream(outputStream);
            
            // Write thumbnail to file
            Files.copy(
                new ByteArrayInputStream(outputStream.toByteArray()),
                targetPath,
                StandardCopyOption.REPLACE_EXISTING
            );
            
            log.info("Image saved successfully: {}", filename);
            return filename;
            
        } catch (IOException e) {
            log.error("Failed to save image for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to save image", e);
        }
    }

    /**
     * Retrieves an image by item ID.
     *
     * @param itemId The ID of the item
     * @return The image file as a Resource
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
                log.debug("Image retrieved for item ID: {}", itemId);
                return resource;
            } else {
                log.warn("Image not found for item ID: {}", itemId);
                throw new FileStorageException("Image not found");
            }
        } catch (IOException e) {
            log.error("Failed to retrieve image for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to retrieve image", e);
        }
    }

    /**
     * Deletes an image file.
     *
     * @param imagePath The path to the image file (filename)
     */
    public void deleteImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        
        try {
            Path filePath = this.storageLocation.resolve(imagePath).normalize();
            
            // Prevent path traversal
            if (!filePath.startsWith(this.storageLocation)) {
                throw new FileStorageException("Invalid file path");
            }
            
            Files.deleteIfExists(filePath);
            log.info("Image deleted: {}", imagePath);
        } catch (IOException e) {
            log.error("Failed to delete image: {}", imagePath, e);
            throw new FileStorageException("Failed to delete image", e);
        }
    }
}
