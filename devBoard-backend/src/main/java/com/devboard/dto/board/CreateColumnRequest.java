package com.devboard.dto.board;

import com.devboard.entity.enums.ColumnRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateColumnRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 1, max = 50, message = "Nome deve ter entre 1 e 50 caracteres")
    private String name;

    private String color;

    private Integer position;

    private ColumnRole role;

    private Integer wipLimit;
}
