--liquibase formatted sql

--changeset devboard:6-board-github-domain
-- Abort on ambiguous legacy data rather than silently losing a repository or renaming boards.
ALTER TABLE boards ADD CONSTRAINT uq_boards_project_name UNIQUE (project_id, name);
CREATE UNIQUE INDEX uq_boards_project_default ON boards(project_id) WHERE is_default = TRUE;

INSERT INTO boards(project_id, name, is_default)
SELECT p.id, 'Main Board', TRUE FROM projects p
WHERE NOT EXISTS (SELECT 1 FROM boards b WHERE b.project_id = p.id);
UPDATE boards b SET is_default = TRUE
WHERE b.id = (SELECT MIN(b2.id) FROM boards b2 WHERE b2.project_id = b.project_id)
AND NOT EXISTS (SELECT 1 FROM boards b3 WHERE b3.project_id = b.project_id AND b3.is_default);

ALTER TABLE boards ADD COLUMN github_repo_id BIGINT;
ALTER TABLE boards ADD COLUMN github_repo_owner VARCHAR(255);
ALTER TABLE boards ADD COLUMN github_repo_name VARCHAR(255);
ALTER TABLE boards ADD COLUMN github_repo_url TEXT;
ALTER TABLE boards ADD COLUMN default_base_branch VARCHAR(255) NOT NULL DEFAULT 'main';
ALTER TABLE boards ADD COLUMN last_sync_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE boards ADD COLUMN github_user_id BIGINT REFERENCES users(id);
ALTER TABLE boards ADD COLUMN github_hook_id BIGINT;
ALTER TABLE boards ADD COLUMN github_reauth_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE boards ADD COLUMN move_on_commit BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boards ADD COLUMN move_on_pr_open BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boards ADD COLUMN move_on_pr_merge BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boards ADD COLUMN import_issues BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boards ADD COLUMN close_issue_on_done BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boards ADD COLUMN branch_pattern VARCHAR(255) NOT NULL DEFAULT 'feature/task-{id}-{title}';

UPDATE boards b SET github_repo_id = p.github_repo_id, github_repo_owner = p.github_repo_owner,
    github_repo_name = p.github_repo_name, github_repo_url = p.github_repo_url,
    default_base_branch = p.default_base_branch, last_sync_at = p.last_sync_at,
    github_user_id = CASE WHEN p.github_repo_id IS NOT NULL THEN p.owner_id END
FROM projects p WHERE b.project_id = p.id AND b.is_default;
ALTER TABLE boards ADD CONSTRAINT uq_boards_github_repo_id UNIQUE (github_repo_id);
CREATE INDEX idx_boards_github_user_id ON boards(github_user_id);

CREATE TABLE board_watched_branches (
    board_id BIGINT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    branch_name VARCHAR(255) NOT NULL
);
CREATE INDEX idx_board_watched_branches_board_id ON board_watched_branches(board_id);
INSERT INTO board_watched_branches(board_id, branch_name)
SELECT b.id, w.branch_name FROM project_watched_branches w
JOIN boards b ON b.project_id = w.project_id AND b.is_default AND b.github_repo_id IS NOT NULL;
DROP TABLE project_watched_branches;
ALTER TABLE projects DROP COLUMN github_repo_id, DROP COLUMN github_repo_owner,
    DROP COLUMN github_repo_name, DROP COLUMN github_repo_url,
    DROP COLUMN default_base_branch, DROP COLUMN last_sync_at;

ALTER TABLE tasks ADD COLUMN github_issue_id BIGINT;
ALTER TABLE tasks ADD COLUMN github_issue_number INTEGER;
ALTER TABLE tasks ADD COLUMN github_issue_url TEXT;
ALTER TABLE tasks ADD COLUMN github_pr_id BIGINT;
ALTER TABLE tasks ADD COLUMN github_pr_url TEXT;
ALTER TABLE tasks ADD COLUMN github_pr_state VARCHAR(50);
ALTER TABLE tasks ADD COLUMN branch VARCHAR(255);
ALTER TABLE tasks ADD COLUMN github_manually_edited BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_tasks_github_issue_id ON tasks(github_issue_id);
CREATE INDEX idx_tasks_branch ON tasks(branch);

CREATE TABLE github_deliveries (
    id VARCHAR(255) PRIMARY KEY,
    board_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_github_deliveries_created_at ON github_deliveries(created_at);
CREATE INDEX idx_github_deliveries_board_id ON github_deliveries(board_id);
CREATE TABLE github_outgoing_operations (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_github_outgoing_issue ON github_outgoing_operations(repository_id, issue_id, action);
