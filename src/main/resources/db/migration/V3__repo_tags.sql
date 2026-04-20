-- Add tags and description to exemplar_repos for organization
ALTER TABLE exemplar_repos ADD COLUMN tags VARCHAR(2000);
ALTER TABLE exemplar_repos ADD COLUMN notes CLOB;
