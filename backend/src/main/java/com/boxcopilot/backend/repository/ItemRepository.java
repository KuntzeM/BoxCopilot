package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
	List<Item> findByNameContainingIgnoreCase(String namePart);
	List<Item> findByBox_UuidAndNameContainingIgnoreCase(String boxUuid, String namePart);
	List<Item> findByBoxId(Long boxId);
}
