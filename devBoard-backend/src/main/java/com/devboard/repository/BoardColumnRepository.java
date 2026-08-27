package com.devboard.repository;

import com.devboard.entity.BoardColumn;
import com.devboard.entity.enums.ColumnRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoardIdOrderByPositionAsc(Long boardId);

    boolean existsByBoardIdAndName(Long boardId, String name);

    boolean existsByBoardIdAndNameAndIdNot(Long boardId, String name, Long id);

    boolean existsByBoardIdAndRole(Long boardId, ColumnRole role);

    boolean existsByBoardIdAndRoleAndIdNot(Long boardId, ColumnRole role, Long id);

    long countByBoardId(Long boardId);
}
