package com.boxcopilot.backend.service;

import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.mapper.BoxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoxServiceTest {

    @Autowired
    private BoxService boxService;

    @Autowired
    private BoxRepository boxRepository;

    @Autowired
    private BoxMapper boxMapper;

    @Test
    void testCreateBox_withHandlingFlags() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Küche");
        request.setTargetRoom("Keller");
        request.setDescription("Fragile items");
        request.setIsFragile(true);
        request.setNoStack(true);
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCurrentRoom()).isEqualTo("Küche");
        assertThat(result.getTargetRoom()).isEqualTo("Keller");
        assertThat(result.getDescription()).isEqualTo("Fragile items");
        assertThat(result.getIsFragile()).isTrue();
        assertThat(result.getNoStack()).isTrue();
    }

    @Test
    void testCreateBox_withDefaultHandlingFlags() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Wohnzimmer");
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getIsFragile()).isFalse();
        assertThat(result.getNoStack()).isFalse();
    }

    @Test
    void testUpdateBox_withHandlingFlags() {
        // Given - Create a box first
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Schlafzimmer");
        createRequest.setIsFragile(false);
        createRequest.setNoStack(false);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update the box with fragile and noStack flags
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setIsFragile(true);
        updateRequest.setNoStack(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsFragile()).isTrue();
        assertThat(updated.getNoStack()).isTrue();
        assertThat(updated.getCurrentRoom()).isEqualTo("Schlafzimmer");
    }

    @Test
    void testUpdateBox_partialUpdate() {
        // Given - Create a box with flags
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Garage");
        createRequest.setIsFragile(true);
        createRequest.setNoStack(false);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update only noStack flag
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setNoStack(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsFragile()).isTrue(); // Should remain unchanged
        assertThat(updated.getNoStack()).isTrue();   // Should be updated
    }
}
