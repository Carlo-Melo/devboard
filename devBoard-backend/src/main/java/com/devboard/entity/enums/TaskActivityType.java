package com.devboard.entity.enums;

/**
 * GitHub e labels ainda não existem no sistema (spec-github-integration.md e spec-labels-search.md
 * são Sprint 2/3) — tipos de atividade ligados a eles entram junto com esses módulos.
 */
public enum TaskActivityType {
    CREATED,
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    TYPE_CHANGED,
    PRIORITY_CHANGED,
    ASSIGNEE_CHANGED,
    COLLABORATORS_CHANGED,
    DUE_DATE_CHANGED,
    ESTIMATE_CHANGED,
    MOVED,
    COMMENTED,
    ARCHIVED
}
