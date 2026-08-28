package com.devboard.event;

import com.devboard.entity.Task;
import com.devboard.entity.enums.TaskActivityType;
import lombok.Getter;

import java.util.Map;

/**
 * Evento genérico de atividade de tarefa. Um único tipo de evento cobre todos os tipos de
 * atividade (spec-tasks.md 3.4) em vez de uma classe por campo alterado — o {@link TaskActivityType}
 * e a descrição já gerada em texto carregam a distinção necessária.
 */
@Getter
public class TaskActivityEvent {

    private final Task task;
    private final Long actorUserId;
    private final TaskActivityType type;
    private final String description;
    private final Map<String, Object> metadata;

    public TaskActivityEvent(Task task, Long actorUserId, TaskActivityType type, String description) {
        this(task, actorUserId, type, description, null);
    }

    public TaskActivityEvent(Task task, Long actorUserId, TaskActivityType type, String description, Map<String, Object> metadata) {
        this.task = task;
        this.actorUserId = actorUserId;
        this.type = type;
        this.description = description;
        this.metadata = metadata;
    }
}
