package com.devboard.dto.board;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReorderColumnsRequest {

    @NotEmpty(message = "Lista de colunas é obrigatória")
    private List<Long> columnIds;
}
