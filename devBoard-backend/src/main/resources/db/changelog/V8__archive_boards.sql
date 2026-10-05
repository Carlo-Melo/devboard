--liquibase formatted sql

--changeset devboard:8-archive-boards
ALTER TABLE boards ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE boards ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_boards_project_archived ON boards(project_id, archived);
ALTER TABLE boards DROP CONSTRAINT uq_boards_github_repo_id;
CREATE UNIQUE INDEX uq_boards_active_github_repo_id ON boards(github_repo_id)
    WHERE archived = FALSE AND github_repo_id IS NOT NULL;
ALTER TABLE boards ADD CONSTRAINT ck_boards_default_active CHECK (NOT is_default OR NOT archived);
