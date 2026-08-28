package com.devboard.async;

import com.devboard.event.TaskActivityEvent;
import com.devboard.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ouve {@link TaskActivityEvent} depois do commit da transação que o originou, para que a
 * atividade nunca registre algo que acabou de sofrer rollback (claude.md — Processamento Assíncrono).
 */
@Component
@RequiredArgsConstructor
public class TaskActivityListener {

    private final ActivityService activityService;

    @Async("activityExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskActivity(TaskActivityEvent event) {
        activityService.record(event);
    }
}
