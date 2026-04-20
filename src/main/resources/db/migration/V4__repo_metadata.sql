-- Add GitHub metadata to repos for richer querying
ALTER TABLE exemplar_repos ADD COLUMN language VARCHAR(100);
ALTER TABLE exemplar_repos ADD COLUMN is_fork BOOLEAN DEFAULT FALSE;
ALTER TABLE exemplar_repos ADD COLUMN is_private BOOLEAN DEFAULT FALSE;
ALTER TABLE exemplar_repos ADD COLUMN stars INT DEFAULT 0;
ALTER TABLE exemplar_repos ADD COLUMN github_description VARCHAR(2000);
