package com.devboard.repository;

import com.devboard.entity.ProjectInvite;
import com.devboard.entity.enums.InviteStatus;
import com.devboard.entity.enums.InviteType;
import com.devboard.entity.enums.ProjectRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInviteRepository extends JpaRepository<ProjectInvite, Long> {

    @EntityGraph(attributePaths = {"project", "invitedBy"})
    Optional<ProjectInvite> findByToken(String token);

    @EntityGraph(attributePaths = {"project", "invitedBy"})
    Optional<ProjectInvite> findByIdAndProjectId(Long id, Long projectId);

    @EntityGraph(attributePaths = {"invitedBy"})
    List<ProjectInvite> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, InviteStatus status);

    @EntityGraph(attributePaths = {"invitedBy"})
    List<ProjectInvite> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectInvite> findFirstByProjectIdAndTypeAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            Long projectId, InviteType type, String email, InviteStatus status);

    Optional<ProjectInvite> findFirstByProjectIdAndTypeAndRoleAndStatusOrderByCreatedAtDesc(
            Long projectId, InviteType type, ProjectRole role, InviteStatus status);
}
