--liquibase formatted sql

--changeset devboard:3-create-projects-tables
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL,

    github_repo_id BIGINT,
    github_repo_owner VARCHAR(255),
    github_repo_name VARCHAR(255),
    github_repo_url TEXT,

    default_base_branch VARCHAR(255) NOT NULL DEFAULT 'main',

    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    last_sync_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_projects_owner_id FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_projects_archived ON projects(archived);
--rollback DROP TABLE projects;

--changeset devboard:3-create-project-watched-branches-table
CREATE TABLE project_watched_branches (
    project_id BIGINT NOT NULL,
    branch_name VARCHAR(255) NOT NULL,

    CONSTRAINT fk_project_watched_branches_project_id FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_project_watched_branches_project_id ON project_watched_branches(project_id);
--rollback DROP TABLE project_watched_branches;

--changeset devboard:3-create-project-members-table
CREATE TABLE project_members (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,

    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_project_members_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_members_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_project_members_project_id_user_id UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_members_project_id ON project_members(project_id);
CREATE INDEX idx_project_members_user_id ON project_members(user_id);
--rollback DROP TABLE project_members;

--changeset devboard:3-create-boards-table
CREATE TABLE boards (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_boards_project_id FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_boards_project_id ON boards(project_id);
--rollback DROP TABLE boards;

--changeset devboard:3-create-board-columns-table
CREATE TABLE board_columns (
    id BIGSERIAL PRIMARY KEY,
    board_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7),
    position INTEGER NOT NULL,
    semantic_role VARCHAR(50) NOT NULL DEFAULT 'NONE',
    wip_limit INTEGER,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_board_columns_board_id FOREIGN KEY (board_id) REFERENCES boards(id),
    CONSTRAINT uq_board_columns_board_id_name UNIQUE (board_id, name)
);

CREATE INDEX idx_board_columns_board_id ON board_columns(board_id);
--rollback DROP TABLE board_columns;
