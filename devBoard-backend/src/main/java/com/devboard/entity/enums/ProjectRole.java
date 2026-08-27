package com.devboard.entity.enums;

public enum ProjectRole {
    VIEWER(1),
    DEVELOPER(2),
    ADMIN(3);

    private final int level;

    ProjectRole(int level) {
        this.level = level;
    }

    public boolean atLeast(ProjectRole minimum) {
        return this.level >= minimum.level;
    }
}
