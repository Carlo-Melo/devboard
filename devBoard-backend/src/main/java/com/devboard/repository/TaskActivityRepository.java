package com.devboard.repository;

import com.devboard.entity.TaskActivity;
import com.devboard.entity.enums.TaskActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    @org.springframework.data.jpa.repository.Query("select new com.devboard.dto.project.DashboardActivityResponse(a.id, t.id, t.title, p.id, p.name, coalesce(u.fullName, u.username), cast(a.type as string), a.description, a.createdAt) from TaskActivity a join a.task t join t.column c join c.board b join b.project p left join a.author u left join com.devboard.entity.ProjectMember pm on pm.project = p and pm.user.id = :userId where b.archived = false and p.archived = false and (p.owner.id = :userId or pm.id is not null) and (:projectId is null or p.id = :projectId) order by a.createdAt desc")
    Page<com.devboard.dto.project.DashboardActivityResponse> findDashboardActivities(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("projectId") Long projectId, Pageable pageable);

    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    Page<TaskActivity> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);

    Page<TaskActivity> findByTaskIdAndTypeOrderByCreatedAtDesc(Long taskId, TaskActivityType type, Pageable pageable);
}
