package com.devboard.repository;

import com.devboard.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByColumnIdAndArchivedFalseOrderByPositionAsc(Long columnId);

    long countByColumnIdAndArchivedFalse(Long columnId);

    /**
     * Leitura do quadro: uma única consulta com JOIN FETCH para todas as colunas do board,
     * nunca uma consulta por coluna nem por tarefa (claude.md — Paginação).
     */
    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.assignee "
            + "WHERE t.column.id IN :columnIds AND t.archived = false "
            + "ORDER BY t.position ASC")
    List<Task> findByColumnIdInAndArchivedFalseOrderByPosition(@Param("columnIds") List<Long> columnIds);

    @Query("SELECT t FROM Task t "
            + "LEFT JOIN FETCH t.assignee "
            + "LEFT JOIN FETCH t.creator "
            + "LEFT JOIN FETCH t.column c "
            + "LEFT JOIN FETCH c.board b "
            + "LEFT JOIN FETCH b.project "
            + "WHERE t.id = :id")
    Optional<Task> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT t FROM Task t JOIN FETCH t.column c JOIN FETCH c.board b "
            + "WHERE t.assignee.id = :userId AND b.project.id = :projectId AND t.archived = false")
    List<Task> findActiveByProjectIdAndAssigneeId(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
