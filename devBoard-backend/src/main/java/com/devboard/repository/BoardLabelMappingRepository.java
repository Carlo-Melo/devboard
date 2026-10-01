package com.devboard.repository;
import com.devboard.entity.BoardLabelMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BoardLabelMappingRepository extends JpaRepository<BoardLabelMapping, Long> {
    Optional<BoardLabelMapping> findByLabelIdAndBoardId(Long labelId, Long boardId);
    void deleteByBoardId(Long boardId);
    void deleteByLabelId(Long labelId);
}
