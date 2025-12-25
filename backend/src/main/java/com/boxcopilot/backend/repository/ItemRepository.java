package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
	List<Item> findByNameContainingIgnoreCaseOrderByNameAsc(String namePart);
	List<Item> findByBox_UuidAndNameContainingIgnoreCaseOrderByNameAsc(String boxUuid, String namePart);
	List<Item> findByBoxIdOrderByNameAsc(Long boxId);
}
