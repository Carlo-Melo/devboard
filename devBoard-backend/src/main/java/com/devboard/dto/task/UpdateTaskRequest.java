package com.devboard.dto.task;

import com.devboard.entity.enums.TaskPriority;
import com.devboard.entity.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * PUT de substituição integral dos campos editáveis (mesmo padrão de UpdateColumnRequest):
 * enviar o valor atual para o que não deve mudar, null limpa o campo. Criador, coluna, posição
 * e identificadores do GitHub não são editáveis por aqui (spec-tasks.md 4.3).
 */
@Data
public class UpdateTaskRequest {

    @NotBlank(message = "Título é obrigatório")
    @Size(min = 3, max = 255, message = "Título deve ter entre 3 e 255 caracteres")
    private String title;

    private String description;

    private TaskType type;

    private TaskPriority priority;

    private Long assigneeId;

    private List<Long> collaboratorIds;

    private LocalDate dueDate;

    private Integer estimate;
}
