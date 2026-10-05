package com.devboard.repository;

import com.devboard.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("select count(distinct p.id) from Project p left join ProjectMember pm on pm.project = p and pm.user.id = :userId where p.archived = false and (p.owner.id = :userId or pm.id is not null)")
    long countAccessibleActive(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    java.util.Optional<Project> findLockedById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT p FROM Project p
            LEFT JOIN ProjectMember pm ON pm.project = p AND pm.user.id = :userId
            WHERE (p.owner.id = :userId OR pm.id IS NOT NULL)
            AND p.archived = :archived
            """)
    Page<Project> findAccessibleByUser(@Param("userId") Long userId, @Param("archived") boolean archived, Pageable pageable);
}
