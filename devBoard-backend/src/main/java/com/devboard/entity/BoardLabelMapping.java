package com.devboard.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
@Entity @Table(name = "board_label_mappings", uniqueConstraints = @UniqueConstraint(columnNames = {"label_id", "board_id"}))
@Getter @Setter @NoArgsConstructor
public class BoardLabelMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "label_id", nullable = false) private Label label;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "board_id", nullable = false) private Board board;
    private Long githubLabelId;
    private String githubLabelName;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
