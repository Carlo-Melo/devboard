package com.devboard.dto.task;

import com.devboard.entity.enums.TaskPriority;
import com.devboard.entity.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateTaskRequest {

    @NotNull(message = "Coluna é obrigatória")
    private Long columnId;

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
