--liquibase formatted sql

--changeset devboard:4-create-tasks-table
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    column_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    position INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    assignee_id BIGINT,
    creator_id BIGINT NOT NULL,
    due_date DATE,
    estimate INTEGER,

    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tasks_column_id FOREIGN KEY (column_id) REFERENCES board_columns(id),
    CONSTRAINT fk_tasks_assignee_id FOREIGN KEY (assignee_id) REFERENCES users(id),
    CONSTRAINT fk_tasks_creator_id FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE INDEX idx_tasks_column_id ON tasks(column_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_tasks_creator_id ON tasks(creator_id);
CREATE INDEX idx_tasks_archived ON tasks(archived);
--rollback DROP TABLE tasks;

--changeset devboard:4-create-task-collaborators-table
CREATE TABLE task_collaborators (
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    PRIMARY KEY (task_id, user_id),
    CONSTRAINT fk_task_collaborators_task_id FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_task_collaborators_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_task_collaborators_task_id ON task_collaborators(task_id);
CREATE INDEX idx_task_collaborators_user_id ON task_collaborators(user_id);
--rollback DROP TABLE task_collaborators;

--changeset devboard:4-create-task-comments-table
CREATE TABLE task_comments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    edited BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_task_comments_task_id FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_task_comments_author_id FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_task_comments_task_id ON task_comments(task_id);
CREATE INDEX idx_task_comments_author_id ON task_comments(author_id);
--rollback DROP TABLE task_comments;

--changeset devboard:4-create-task-activities-table
CREATE TABLE task_activities (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_id BIGINT,
    type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    metadata TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_task_activities_task_id FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_task_activities_author_id FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_task_activities_task_id ON task_activities(task_id);
--rollback DROP TABLE task_activities;
