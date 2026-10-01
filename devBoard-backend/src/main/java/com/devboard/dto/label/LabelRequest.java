package com.devboard.dto.label;
import jakarta.validation.constraints.*;
public record LabelRequest(@NotBlank @Size(max = 50) String name,
                           @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color, String description) {}
