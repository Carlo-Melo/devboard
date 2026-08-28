package com.devboard.entity.enums;

public enum TaskType {
    DEV(true),
    QA(false),
    DESIGN(false),
    DOCUMENTATION(true),
    OPERATIONAL(false),
    OTHER(false);

    private final boolean supportsBranch;

    TaskType(boolean supportsBranch) {
        this.supportsBranch = supportsBranch;
    }

    public boolean supportsBranch() {
        return supportsBranch;
    }
}
