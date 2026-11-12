package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Item;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
	@EntityGraph(attributePaths = "box")
	List<Item> findByNameContainingIgnoreCaseOrderByNameAsc(String namePart);
	@EntityGraph(attributePaths = "box")
	List<Item> findByBox_UuidAndNameContainingIgnoreCaseOrderByNameAsc(String boxUuid, String namePart);
	@EntityGraph(attributePaths = "box")
	List<Item> findByBoxIdOrderByNameAsc(Long boxId);
	@EntityGraph(attributePaths = "box")
	List<Item> findByBox_UuidOrderByNameAsc(String boxUuid);
	Optional<Item> findByImageToken(String imageToken);

	/** Ids of legacy items whose image token is NULL or blank (startup backfill); ids only, so no box (LOB) is read. */
	@Query("select i.id from Item i where i.imageToken is null or trim(i.imageToken) = ''")
	List<Long> findIdsWithoutImageToken();

	@Modifying
	@Query("update Item i set i.imageToken = :token where i.id = :id")
	void updateImageToken(@Param("id") Long id, @Param("token") String token);

	/** Bounded name search that loads each item's box in the same statement. */
	@EntityGraph(attributePaths = "box")
	List<Item> findByNameContainingIgnoreCase(String namePart, Pageable pageable);

	@Query("select i.box.id as boxId, count(i) as itemCount from Item i where i.box.id in :boxIds group by i.box.id")
	List<BoxItemCount> countItemsByBoxIds(@Param("boxIds") Collection<Long> boxIds);
}
