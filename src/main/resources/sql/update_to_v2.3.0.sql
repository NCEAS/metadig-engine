ALTER TABLE filestore DROP CONSTRAINT IF EXISTS all_properties_fk;
ALTER TABLE filestore RENAME COLUMN collection_id to pid;
ALTER TABLE filestore DROP column metadata_id;
-- ALTER TABLE filestore ADD CONSTRAINT all_properties_fk UNIQUE (pid, suite_id, node_id, format_filter, storage_type, media_type, alt_filename);
ALTER TABLE filestore ADD CONSTRAINT all_properties_fk UNIQUE (pid, storage_type, media_type, alt_filename);

