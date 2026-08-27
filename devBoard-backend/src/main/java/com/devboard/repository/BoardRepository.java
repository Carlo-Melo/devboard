package com.devboard.repository;

import com.devboard.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Board> findByProjectId(Long projectId);

    Optional<Board> findByProjectIdAndIsDefaultTrue(Long projectId);

    long countByProjectId(Long projectId);
}
