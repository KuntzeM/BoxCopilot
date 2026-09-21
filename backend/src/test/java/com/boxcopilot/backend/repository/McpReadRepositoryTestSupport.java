package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.UUID;

/** Fixture helpers shared by the repository slice tests of the MCP read layer. */
final class McpReadRepositoryTestSupport {

    private McpReadRepositoryTestSupport() {
    }

    static Box box(TestEntityManager em, int number, String currentRoom, String targetRoom, String description) {
        Box box = new Box(UUID.randomUUID().toString(), currentRoom, targetRoom, description);
        box.setBoxNumber(number);
        return em.persist(box);
    }

    static Item item(TestEntityManager em, Box box, String name) {
        Item item = em.persist(new Item(box, name));
        box.getItems().add(item);
        return item;
    }
}
