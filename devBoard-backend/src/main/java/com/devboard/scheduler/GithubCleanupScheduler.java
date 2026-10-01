package com.devboard.scheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @EnableScheduling
public class GithubCleanupScheduler {
    private final JdbcTemplate jdbc;
    @Scheduled(cron = "0 0 3 * * *") @Transactional
    public void cleanup() {
        jdbc.update("DELETE FROM github_deliveries WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '7 days'");
        jdbc.update("DELETE FROM github_outgoing_operations WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 hour'");
    }
}
