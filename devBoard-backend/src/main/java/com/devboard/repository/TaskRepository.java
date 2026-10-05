package com.devboard.repository;

import com.devboard.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @Query("select count(distinct b.project.id) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where (b.project.owner.id = :userId or pm.id is not null) and b.archived = false and b.project.archived = false")
    long countAccessibleProjects(@Param("userId") Long userId);

    @Query("select c.role, count(t) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and b.project.archived = false and (b.project.owner.id = :userId or pm.id is not null) and (:projectId is null or b.project.id = :projectId) group by c.role")
    java.util.List<Object[]> countMyTasksByRole(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Query("select t.priority, count(t) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and b.project.archived = false and c.role <> com.devboard.entity.enums.ColumnRole.DONE and (b.project.owner.id = :userId or pm.id is not null) and (:projectId is null or b.project.id = :projectId) group by t.priority")
    java.util.List<Object[]> countMyPendingByPriority(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Query("select count(t) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and b.project.archived = false and c.role <> com.devboard.entity.enums.ColumnRole.DONE and (b.project.owner.id = :userId or pm.id is not null) and (:projectId is null or b.project.id = :projectId)")
    long countMyPending(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Query("select count(t) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and b.project.archived = false and c.role <> com.devboard.entity.enums.ColumnRole.DONE and t.dueDate < :today and (b.project.owner.id = :userId or pm.id is not null) and (:projectId is null or b.project.id = :projectId)")
    long countMyOverdue(@Param("userId") Long userId, @Param("today") java.time.LocalDate today, @Param("projectId") Long projectId);

    @Query("select count(t) from Task t join t.column c join c.board b left join com.devboard.entity.ProjectMember pm on pm.project = b.project and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and b.project.archived = false and c.role <> com.devboard.entity.enums.ColumnRole.DONE and t.dueDate between :today and :week and (b.project.owner.id = :userId or pm.id is not null) and (:projectId is null or b.project.id = :projectId)")
    long countMyDueSoon(@Param("userId") Long userId, @Param("today") java.time.LocalDate today, @Param("week") java.time.LocalDate week, @Param("projectId") Long projectId);

    @Query("select new com.devboard.dto.project.DashboardTaskResponse(t.id, t.title, p.id, p.name, b.id, b.name, c.name, cast(c.role as string), cast(t.priority as string), t.dueDate) from Task t join t.column c join c.board b join b.project p left join com.devboard.entity.ProjectMember pm on pm.project = p and pm.user.id = :userId where t.assignee.id = :userId and t.archived = false and b.archived = false and p.archived = false and c.role <> com.devboard.entity.enums.ColumnRole.DONE and (p.owner.id = :userId or pm.id is not null) and (:projectId is null or p.id = :projectId) order by case when t.dueDate < :today then 0 when t.dueDate is null then 2 else 1 end, t.dueDate asc, case t.priority when com.devboard.entity.enums.TaskPriority.URGENT then 4 when com.devboard.entity.enums.TaskPriority.HIGH then 3 when com.devboard.entity.enums.TaskPriority.MEDIUM then 2 else 1 end desc")
    org.springframework.data.domain.Page<com.devboard.dto.project.DashboardTaskResponse> findMyDashboardTasks(@Param("userId") Long userId, @Param("projectId") Long projectId, @Param("today") java.time.LocalDate today, org.springframework.data.domain.Pageable pageable);
    List<Task> findByLabelsId(Long labelId);

    List<Task> findByColumnBoardId(Long boardId);
    boolean existsByColumnBoardId(Long boardId);
    List<Task> findByColumnBoardIdAndArchivedFalse(Long boardId);
    Optional<Task> findByColumnBoardIdAndGithubIssueId(Long boardId, Long issueId);
    boolean existsByColumnBoardIdAndArchivedFalse(Long boardId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findLockedById(@Param("id") Long id);
    List<Task> findByColumnIdAndArchivedFalseOrderByPositionAsc(Long columnId);

    long countByColumnIdAndArchivedFalse(Long columnId);

    /**
     * Leitura do quadro: uma única consulta com JOIN FETCH para todas as colunas do board,
     * nunca uma consulta por coluna nem por tarefa (claude.md — Paginação).
     */
    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN FETCH t.assignee LEFT JOIN FETCH t.labels "
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
            + "WHERE t.assignee.id = :userId AND b.project.id = :projectId AND b.archived = false AND t.archived = false")
    List<Task> findActiveByProjectIdAndAssigneeId(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
