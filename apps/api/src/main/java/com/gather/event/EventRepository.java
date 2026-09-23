package com.gather.event;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id=:id")
  Optional<Event> lock(@Param("id") UUID id);
}
