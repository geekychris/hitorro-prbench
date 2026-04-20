-- Store discovered documentation files per repo
ALTER TABLE exemplar_repos ADD COLUMN docs_json CLOB;
ALTER TABLE exemplar_repos ADD COLUMN docs_scanned_at TIMESTAMP;
