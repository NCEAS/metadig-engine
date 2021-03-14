/*
 * This SQL script creates a database that contains metadata quality
 * reports that are generated by the metadata quality engine.
 */

/* CREATE SEQUENCE id_seq; */

CREATE USER metadig;

CREATE DATABASE metadig OWNER metadig;

/* alter database metadig owner to metadig; */

\connect metadig

CREATE TABLE identifiers (
  metadata_id TEXT not null,
  data_source TEXT not null,
  obsoletes TEXT,
  obsoleted_by TEXT,
  sequence_id TEXT,
  format_id TEXT,
  rights_holder TEXT,
  date_uploaded TIMESTAMP WITH TIME ZONE,
  date_sysmeta_modified TIMESTAMP WITH TIME ZONE,
  CONSTRAINT metadata_id_pk PRIMARY KEY (metadata_id)
);

alter table identifiers owner to metadig;

/* This table records when a quality assessment job entered the quality engine facility
   and when the job was completed. Note that this table cannot be a child table of the
   'identifier' table, as pid entries in this table might be entered before they
   are entered in the 'identifiers' table.
   */
CREATE TABLE run_queue (
  metadata_id TEXT not null,
  suite_id TEXT not null,
  datetime_completed TEXT;
  datetime_queued TEXT;
  CONSTRAINT run_queue_metadata_id_suite_id_pk PRIMARY KEY (metadata_id, suite_id)
);

alter table run_queue owner to metadig;

create table tasks (
  task_name TEXT not null,
  task_type TEXT not null,
  CONSTRAINT task_name_task_type PRIMARY KEY (task_name, task_type)
);

alter table tasks owner to metadig;

create table node_harvest (
  task_name TEXT not null,
  task_type TEXT not null,
  node_id TEXT not null,
  last_harvest_datetime TEXT not null,
  CONSTRAINT node_harvest_task_name_task_type_fk FOREIGN KEY (task_name, task_type) REFERENCES tasks (task_name, task_type),
  CONSTRAINT node_harvest_task_name_task_type_node_id_uc UNIQUE (task_name, task_type, node_id)
);

alter table node_harvest owner to metadig;

create TABLE runs (
  metadata_id TEXT not null,
  suite_id TEXT not null,
  timestamp TIMESTAMP WITH TIME ZONE,
  results TEXT not null,
  status TEXT not null DEFAULT 'success'::text,
  error TEXT not null,
  CONSTRAINT runs_metadata_id_fk FOREIGN KEY (metadata_id) REFERENCES identifiers,
  CONSTRAINT metadata_id_suite_id_fk UNIQUE (metadata_id, suite_id)
);

alter table runs owner to metadig;

create TABLE filestore (
  file_id TEXT not null,
  pid TEXT not null,
  suite_id TEXT not NULL,
  node_id TEXT not null,
  format_filter TEXT not null,
  creation_datetime TIMESTAMP WITH TIME ZONE not NULL,
  storage_type TEXT not NULL,
  media_type TEXT not NULL,
  alt_filename TEXT not NULL,
  CONSTRAINT file_id_pk PRIMARY KEY (file_id),
  -- CONSTRAINT all_properties_fk UNIQUE (pid, suite_id, node_id, format_filter, storage_type, media_type, alt_filename)
  CONSTRAINT all_properties_fk UNIQUE (pid, storage_type, media_type, alt_filename)
);

alter table filestore owner to metadig;

create TABLE nodes (
  identifier TEXT not null,
  name TEXT not null,
  type TEXT not NULL,
  state TEXT not NULL,
  synchronize boolean not null,
  last_harvest TEXT not null,
  baseURL TEXT not null,
  CONSTRAINT node_id_pk PRIMARY KEY (identifier)
);

alter table nodes owner to metadig;

create TABLE check_results (
  metadata_id TEXT not NULL,
  suite_id TEXT NOT null,
  id TEXT NOT NULL,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  level TEXT NOT null,
  status TEXT NOT null,
  output TEXT NOT null,
  CONSTRAINT checks_metadata_id_suite_id_fk FOREIGN KEY (metadata_id, suite_id) REFERENCES runs,
  CONSTRAINT checks_metadata_id_suite_id_uk UNIQUE (metadata_id, suite_id, id)
);

create index concurrently "checks_metadata_id_suite_id_idx" on checks using btree (metadata_id, suite_id);

alter table nodes owner to metadig;
