package com.boxcopilot.backend.repository;

/** Number of items stored in one box, produced by a grouped count query. */
public interface BoxItemCount {
    Long getBoxId();

    Long getItemCount();
}
