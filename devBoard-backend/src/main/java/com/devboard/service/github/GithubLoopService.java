package com.devboard.service.github;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor
public class GithubLoopService {
    private final JdbcTemplate jdbc;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mark(Long repositoryId, Long issueId, String action) {
        jdbc.update("INSERT INTO github_outgoing_operations(repository_id, issue_id, action) VALUES (?, ?, ?)", repositoryId, issueId, action);
    }
}
