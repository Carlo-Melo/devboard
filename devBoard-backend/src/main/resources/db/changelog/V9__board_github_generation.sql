--liquibase formatted sql

--changeset devboard:9-board-github-generation
ALTER TABLE boards ADD COLUMN github_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE github_deliveries ADD COLUMN board_generation BIGINT NOT NULL DEFAULT 0;
