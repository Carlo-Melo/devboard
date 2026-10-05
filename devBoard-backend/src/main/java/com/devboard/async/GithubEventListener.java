package com.devboard.async;
import com.devboard.event.*;
import com.devboard.exception.*;
import com.devboard.service.github.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component @RequiredArgsConstructor @Slf4j
public class GithubEventListener {
    private final GithubJobService jobs;
    private final GithubEventProcessor processor;
    @Async("githubExecutor") @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void job(GithubJobEvent event) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try { jobs.execute(event); return; }
            catch (UnauthorizedException e) {
                try { jobs.requireReauthentication(event.boardId(), event.repositoryId(), event.generation()); }
                catch (Exception ignored) { log.error("Falha ao marcar reautenticação: boardId={}", event.boardId()); }
                log.warn("GitHub requer reautenticação: boardId={}", event.boardId()); return;
            } catch (ExternalServiceException | RateLimitExceededException e) {
                log.warn("Falha transitória GitHub: boardId={}, operação={}, tentativa={}", event.boardId(), event.operation(), attempt);
                if (attempt == 3 || !pause(attempt)) return;
            } catch (Exception e) {
                log.error("Falha GitHub: boardId={}, operação={}, tipo={}", event.boardId(), event.operation(), e.getClass().getSimpleName()); return;
            }
        }
    }
    @Async("githubExecutor") @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void delivery(GithubDeliveryEvent event) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                processor.process(event.deliveryId());
                return;
            } catch (Exception e) {
                log.error("Falha no processamento GitHub; payload preservado na entrega: tentativa={}, tipo={}",
                        attempt, e.getClass().getSimpleName());
                if (attempt == 3 || !pause(attempt)) return;
            }
        }
    }
    private boolean pause(int attempt) {
        try { Thread.sleep(attempt * 1000L); return true; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
}
