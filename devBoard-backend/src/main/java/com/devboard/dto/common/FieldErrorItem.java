package com.devboard.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldErrorItem {

    private String field;
    private String message;
}
