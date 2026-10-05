package com.devboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "boards", indexes = {
        @Index(name = "idx_boards_project_id", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false)
    private Boolean archived = false;
    private LocalDateTime archivedAt;


    @Column(name = "github_repo_id")
    private Long githubRepoId;
    private String githubRepoOwner;
    private String githubRepoName;
    private String githubRepoUrl;
    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "board_watched_branches", joinColumns = @JoinColumn(name = "board_id"))
    @Column(name = "branch_name", nullable = false)
    private java.util.List<String> watchedBranches = new java.util.ArrayList<>();
    private String defaultBaseBranch = "main";
    private LocalDateTime lastSyncAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_user_id")
    private User githubUser;
    private Long githubHookId;
    @Column(nullable = false)
    private long githubGeneration;
    private boolean githubReauthRequired;
    private boolean moveOnCommit = true;
    private boolean moveOnPrOpen = true;
    private boolean moveOnPrMerge = true;
    private boolean importIssues = true;
    private boolean closeIssueOnDone = true;
    private String branchPattern = "feature/task-{id}-{title}";
    public boolean hasGithubRepo() { return githubRepoId != null; }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
