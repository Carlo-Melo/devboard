package com.devboard.repository;
import com.devboard.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LabelRepository extends JpaRepository<Label, Long> {
    List<Label> findByProjectIdOrderByNameAsc(Long projectId);
    Optional<Label> findByProjectIdAndNameIgnoreCase(Long projectId, String name);
}
