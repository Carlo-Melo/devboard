package com.devboard.repository;

import com.devboard.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Board> findByProjectId(Long projectId);

    Optional<Board> findByProjectIdAndIsDefaultTrue(Long projectId);

    Optional<Board> findByGithubRepoId(Long githubRepoId);
    boolean existsByGithubRepoId(Long githubRepoId);
    boolean existsByProjectIdAndNameAndIdNot(Long projectId, String name, Long id);
    boolean existsByProjectIdAndName(Long projectId, String name);
    long countByProjectIdAndGithubRepoIdIsNotNull(Long projectId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Board b where b.id = :id")
    Optional<Board> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    long countByProjectId(Long projectId);
}
