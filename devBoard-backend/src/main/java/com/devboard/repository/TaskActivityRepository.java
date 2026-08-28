package com.devboard.repository;

import com.devboard.entity.TaskActivity;
import com.devboard.entity.enums.TaskActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    Page<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);

    Page<TaskActivity> findByTaskIdAndTypeOrderByCreatedAtDesc(Long taskId, TaskActivityType type, Pageable pageable);
}
