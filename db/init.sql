-- Table: raw_log

DROP TABLE raw_log;

CREATE TABLE raw_log
(
  id serial NOT NULL,
  user_id character(36),
  session_id character(36),
  event_time timestamp with time zone,
  event character varying(1000),
  CONSTRAINT primkey_id PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE raw_log
  OWNER TO dmitry;

-- Index: idx_session_id

-- DROP INDEX idx_session_id;

CREATE INDEX idx_session_id
  ON raw_log
  USING btree
  (session_id COLLATE pg_catalog."default");

-- Index: idx_user_id

-- DROP INDEX idx_user_id;

CREATE INDEX idx_user_id
  ON raw_log
  USING btree
  (user_id COLLATE pg_catalog."default");

