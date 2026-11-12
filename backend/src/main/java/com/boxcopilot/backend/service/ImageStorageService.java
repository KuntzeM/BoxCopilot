package com.boxcopilot.backend.service;

import com.boxcopilot.backend.exception.FileStorageException;
import com.boxcopilot.backend.exception.InvalidImageException;
import com.boxcopilot.backend.exception.UploadBusyException;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private static final long MAX_PIXELS = 25_000_000L;
    private static final String THUMBNAIL_SUFFIX = "_thumb.jpg";
    private static final String LARGE_SUFFIX = "_large.jpg";
    
    @Value("${app.image.storage.path}")
    private String storagePath;
    
    @Value("${app.image.max-concurrent-uploads:2}")
    private int maxConcurrentUploads = 2;

    private Path storageLocation;
    private Semaphore uploadSlots = new Semaphore(2);
    private long slotWaitMillis = 5_000;

    @PostConstruct
    public void init() {
        try {
            this.uploadSlots = new Semaphore(Math.max(1, maxConcurrentUploads));
            this.storageLocation = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(this.storageLocation);
            Path probe = Files.createTempFile(this.storageLocation, ".write-check", null);
            Files.delete(probe);
            log.info("Image storage initialized at: {}", this.storageLocation);
        } catch (IOException e) {
            log.error("Image storage directory is not writable", e);
            throw new FileStorageException("Image storage directory " + storagePath + " is not writable ("
                    + e + "). Fix: chown -R 10001:10001 <host dir of the image volume>", e);
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
        // Bound concurrent decodes (memory); a busy server answers 503 + Retry-After.
        boolean acquired;
        try {
            acquired = uploadSlots.tryAcquire(slotWaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("Image processing interrupted", e);
        }
        if (!acquired) {
            throw new UploadBusyException("Server is busy processing other images, please try again shortly");
        }
        try {
            return store(itemId, file);
        } finally {
            uploadSlots.release();
        }
    }

    private String store(Long itemId, MultipartFile file) {
        log.info("Saving image for item ID: {}", itemId);
        
        if (file.isEmpty()) {
            throw new InvalidImageException("Cannot upload empty file");
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageException("File must be an image");
        }
        
        try {
            String thumbFilename = itemId + THUMBNAIL_SUFFIX;
            Path thumbPath = this.storageLocation.resolve(thumbFilename);
            String largeFilename = itemId + LARGE_SUFFIX;
            Path largePath = this.storageLocation.resolve(largeFilename);

            // Prevent path traversal
            if (!thumbPath.normalize().startsWith(this.storageLocation)
                    || !largePath.normalize().startsWith(this.storageLocation)) {
                throw new FileStorageException("Cannot store file outside storage directory");
            }

            checkDimensions(file);

            // Decode once (large variant), derive the thumbnail from it, and only then touch the disk,
            // so a failure never leaves a half-replaced image behind.
            BufferedImage large = Thumbnails.of(file.getInputStream())
                    .size(LARGE_SIZE, LARGE_SIZE)
                    .asBufferedImage();

            ByteArrayOutputStream largeStream = new ByteArrayOutputStream();
            Thumbnails.of(large)
                    .scale(1)
                    .outputFormat("jpg")
                    .outputQuality(0.85)
                    .toOutputStream(largeStream);

            ByteArrayOutputStream thumbStream = new ByteArrayOutputStream();
            Thumbnails.of(large)
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toOutputStream(thumbStream);

            Files.copy(new ByteArrayInputStream(thumbStream.toByteArray()), thumbPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new ByteArrayInputStream(largeStream.toByteArray()), largePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Images saved successfully: {} and {}", thumbFilename, largeFilename);
            return thumbFilename;
            
        } catch (IOException e) {
            log.error("Failed to save image for item ID: {}", itemId, e);
            throw new FileStorageException("Failed to save image", e);
        }
    }

    /** Reads the header only (no pixel decode) and rejects unreadable or oversized images. */
    private void checkDimensions(MultipartFile file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = in == null ? Collections.emptyIterator() : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidImageException("File is not a readable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new InvalidImageException("Image is too large (max 25 megapixels)");
                }
            } finally {
                reader.dispose();
            }
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
