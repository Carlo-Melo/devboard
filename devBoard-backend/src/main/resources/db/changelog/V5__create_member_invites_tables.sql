--liquibase formatted sql

--changeset devboard:5-add-invited-by-to-project-members
ALTER TABLE project_members ADD COLUMN invited_by_id BIGINT;
ALTER TABLE project_members ADD CONSTRAINT fk_project_members_invited_by_id
    FOREIGN KEY (invited_by_id) REFERENCES users(id);
CREATE INDEX idx_project_members_invited_by_id ON project_members(invited_by_id);
--rollback ALTER TABLE project_members DROP CONSTRAINT fk_project_members_invited_by_id; ALTER TABLE project_members DROP COLUMN invited_by_id;

--changeset devboard:5-create-project-invites-table
CREATE TABLE project_invites (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    token VARCHAR(128) NOT NULL,
    invited_by_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uses INTEGER NOT NULL DEFAULT 0,
    max_uses INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_project_invites_token UNIQUE (token),
    CONSTRAINT fk_project_invites_project_id FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_project_invites_invited_by_id FOREIGN KEY (invited_by_id) REFERENCES users(id),
    CONSTRAINT chk_project_invites_email_by_type CHECK (
        (type = 'EMAIL' AND email IS NOT NULL) OR (type = 'LINK' AND email IS NULL)
    ),
    CONSTRAINT chk_project_invites_max_uses CHECK (max_uses IS NULL OR max_uses > 0)
);
CREATE INDEX idx_project_invites_project_id ON project_invites(project_id);
CREATE INDEX idx_project_invites_status ON project_invites(status);
CREATE INDEX idx_project_invites_email ON project_invites(email);
--rollback DROP TABLE project_invites;
