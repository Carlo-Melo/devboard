package com.devboard.repository;

import com.devboard.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Board> findByProjectId(Long projectId);
    List<Board> findByProjectIdAndArchivedFalseOrderByCreatedAtAsc(Long projectId);
    List<Board> findByProjectIdAndArchivedFalse(Long projectId);
    org.springframework.data.domain.Page<Board> findByProjectIdAndArchivedTrue(Long projectId, org.springframework.data.domain.Pageable pageable);
    long countByProjectIdAndArchivedFalse(Long projectId);
    long countByProjectIdAndArchivedFalseAndGithubRepoIdIsNotNull(Long projectId);

    Optional<Board> findByProjectIdAndIsDefaultTrue(Long projectId);

    Optional<Board> findByGithubRepoId(Long githubRepoId);
    boolean existsByGithubRepoId(Long githubRepoId);
    Optional<Board> findByGithubRepoIdAndArchivedFalse(Long githubRepoId);
    boolean existsByGithubRepoIdAndArchivedFalse(Long githubRepoId);
    @org.springframework.data.jpa.repository.Query("select b.githubRepoId from Board b where b.archived = false and b.githubRepoId in :ids")
    java.util.List<Long> findLinkedGithubRepoIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    @org.springframework.data.jpa.repository.Query("select distinct b from Board b join fetch b.project p left join ProjectMember pm on pm.project = p and pm.user.id = :userId where p.archived = false and b.archived = false and b.githubRepoId is null and (p.owner.id = :userId or (pm.id is not null and pm.role = com.devboard.entity.enums.ProjectRole.ADMIN)) order by p.name, b.name")
    org.springframework.data.domain.Page<Board> findEligibleGithubDestinations(@org.springframework.data.repository.query.Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
    boolean existsByProjectIdAndNameAndIdNot(Long projectId, String name, Long id);
    boolean existsByProjectIdAndName(Long projectId, String name);
    long countByProjectIdAndGithubRepoIdIsNotNull(Long projectId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Board b where b.id = :id")
    Optional<Board> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    long countByProjectId(Long projectId);
}
