package com.boxcopilot.backend.service;

import com.boxcopilot.backend.exception.InvalidImageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageServiceTest {

    @TempDir
    Path dir;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new ImageStorageService();
        ReflectionTestUtils.setField(service, "storagePath", dir.toString());
        service.init();
    }

    private static MockMultipartFile png(int width, int height) throws Exception {
        // 1-bit image: huge pixel count, tiny file
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "a.png", "image/png", out.toByteArray());
    }

    @Test
    void rejectsImagesAboveFortyMegapixels() throws Exception {
        MockMultipartFile bomb = png(8000, 5001);

        assertThatThrownBy(() -> service.saveImage(1L, bomb))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("too large");
        assertThat(dir.resolve("1_thumb.jpg")).doesNotExist();
    }

    @Test
    void garbageUploadKeepsTheExistingImage() throws Exception {
        service.saveImage(1L, png(300, 200));
        byte[] thumb = Files.readAllBytes(dir.resolve("1_thumb.jpg"));
        byte[] large = Files.readAllBytes(dir.resolve("1_large.jpg"));

        MockMultipartFile garbage = new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> service.saveImage(1L, garbage)).isInstanceOf(InvalidImageException.class);
        assertThat(Files.readAllBytes(dir.resolve("1_thumb.jpg"))).isEqualTo(thumb);
        assertThat(Files.readAllBytes(dir.resolve("1_large.jpg"))).isEqualTo(large);
    }

    @Test
    void storesBothVariantsForAValidImage() throws Exception {
        service.saveImage(2L, png(2000, 1000));

        assertThat(ImageIO.read(dir.resolve("2_large.jpg").toFile()).getWidth()).isEqualTo(1024);
        assertThat(ImageIO.read(dir.resolve("2_thumb.jpg").toFile()).getWidth()).isEqualTo(200);
    }
}
