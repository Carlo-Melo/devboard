package com.devboard.repository;

import com.devboard.entity.TaskComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    Page<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId, Pageable pageable);

    long countByTaskId(Long taskId);

    /** Uma única consulta agregada para o quadro inteiro — nunca uma contagem por tarefa. */
    @Query("SELECT c.task.id, COUNT(c) FROM TaskComment c WHERE c.task.id IN :taskIds GROUP BY c.task.id")
    List<Object[]> countByTaskIdIn(@Param("taskIds") List<Long> taskIds);
}
