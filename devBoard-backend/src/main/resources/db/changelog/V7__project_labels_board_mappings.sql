--liquibase formatted sql

--changeset devboard:7-project-labels-board-mappings
CREATE TABLE labels (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_labels_project_id ON labels(project_id);
CREATE UNIQUE INDEX uq_labels_project_name ON labels(project_id, LOWER(name));
CREATE TABLE task_labels (
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    label_id BIGINT NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY(task_id, label_id)
);
CREATE INDEX idx_task_labels_label_id ON task_labels(label_id);
CREATE TABLE board_label_mappings (
    id BIGSERIAL PRIMARY KEY,
    label_id BIGINT NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    board_id BIGINT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    github_label_id BIGINT NOT NULL,
    github_label_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_board_label_mappings_label_board UNIQUE(label_id, board_id)
);
CREATE INDEX idx_board_label_mappings_board_id ON board_label_mappings(board_id);
INSERT INTO labels(project_id, name, color)
SELECT p.id, defaults.name, defaults.color FROM projects p CROSS JOIN
(VALUES ('bug', '#d73a4a'), ('feature', '#0075ca'), ('enhancement', '#a2eeef'),
('documentation', '#0075ca'), ('urgent', '#b60205'), ('blocked', '#5319e7')) AS defaults(name, color);
