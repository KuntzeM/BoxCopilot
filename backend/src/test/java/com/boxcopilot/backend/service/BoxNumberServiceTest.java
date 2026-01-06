package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.BoxNumberPool;
import com.boxcopilot.backend.repository.BoxNumberPoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BoxNumberService.
 */
@ExtendWith(MockitoExtension.class)
class BoxNumberServiceTest {

    @Mock
    private BoxNumberPoolRepository poolRepository;

    @InjectMocks
    private BoxNumberService boxNumberService;

    @Test
    void shouldAssignSmallestAvailableNumber() {
        // Given
        BoxNumberPool pool = new BoxNumberPool();
        pool.setBoxNumber(2);
        pool.setIsAvailable(true);

        when(poolRepository.findFirstByIsAvailableTrueOrderByBoxNumberAsc())
                .thenReturn(Optional.of(pool));

        // When
        Integer result = boxNumberService.getNextAvailableBoxNumber();

        // Then
        assertThat(result).isEqualTo(2);
        verify(poolRepository).save(argThat(p -> 
            p.getBoxNumber().equals(2) && !p.getIsAvailable() && p.getLastUsedAt() != null
        ));
    }

    @Test
    void shouldCreateNewNumberWhenPoolEmpty() {
        // Given
        when(poolRepository.findFirstByIsAvailableTrueOrderByBoxNumberAsc())
                .thenReturn(Optional.empty());
        when(poolRepository.findMaxBoxNumber()).thenReturn(Optional.of(10));

        // When
        Integer result = boxNumberService.getNextAvailableBoxNumber();

        // Then
        assertThat(result).isEqualTo(11);
        verify(poolRepository).save(argThat(p -> 
            p.getBoxNumber().equals(11) && !p.getIsAvailable()
        ));
    }

    @Test
    void shouldCreateFirstNumberWhenNoNumbersExist() {
        // Given
        when(poolRepository.findFirstByIsAvailableTrueOrderByBoxNumberAsc())
                .thenReturn(Optional.empty());
        when(poolRepository.findMaxBoxNumber()).thenReturn(Optional.empty());

        // When
        Integer result = boxNumberService.getNextAvailableBoxNumber();

        // Then
        assertThat(result).isEqualTo(1);
        verify(poolRepository).save(argThat(p -> p.getBoxNumber().equals(1)));
    }

    @Test
    void shouldReleaseNumberBackToPool() {
        // Given
        BoxNumberPool pool = new BoxNumberPool();
        pool.setBoxNumber(5);
        pool.setIsAvailable(false);
        pool.setLastUsedAt(Instant.now());

        when(poolRepository.findById(5)).thenReturn(Optional.of(pool));

        // When
        boxNumberService.releaseBoxNumber(5);

        // Then
        verify(poolRepository).save(argThat(p -> 
            p.getBoxNumber().equals(5) && p.getIsAvailable()
        ));
    }

    @Test
    void shouldHandleNullNumberRelease() {
        // When
        boxNumberService.releaseBoxNumber(null);

        // Then
        verify(poolRepository, never()).findById(any());
        verify(poolRepository, never()).save(any());
    }

    @Test
    void shouldHandleReleaseOfNonExistentNumber() {
        // Given
        when(poolRepository.findById(999)).thenReturn(Optional.empty());

        // When
        boxNumberService.releaseBoxNumber(999);

        // Then
        verify(poolRepository, never()).save(any());
    }
}
