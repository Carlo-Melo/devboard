package com.devboard.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveTaskRequest {

    @NotNull(message = "Coluna de destino é obrigatória")
    private Long columnId;

    @NotNull(message = "Posição de destino é obrigatória")
    private Integer position;
}
