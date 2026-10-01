package com.devboard.mapper;
import com.devboard.dto.label.LabelResponse;
import com.devboard.entity.Label;
public final class LabelMapper {
    private LabelMapper() {}
    public static LabelResponse response(Label label) {
        return new LabelResponse(label.getId(), label.getProject().getId(), label.getName(), label.getColor(), label.getDescription());
    }
}
