package com.devboard.dto.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Comentário não pode ser vazio")
    private String content;
}
