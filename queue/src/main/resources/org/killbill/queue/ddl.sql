/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id serial unique,
    class_name varchar(256) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ default null,
    queue_name varchar(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token varchar(36),
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX idx_comp_where ON notifications(effective_date, processing_state, processing_owner, processing_available_date);
CREATE INDEX idx_update ON notifications(processing_state, processing_owner, processing_available_date);
CREATE INDEX idx_get_ready ON notifications(effective_date, created_date);
CREATE INDEX notifications_search_keys ON notifications(search_key2, search_key1);

DROP TABLE IF EXISTS notifications_history;
CREATE TABLE notifications_history (
    record_id serial unique,
    class_name varchar(256) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ default null,
    queue_name varchar(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token varchar(36),
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX notifications_history_search_keys ON notifications_history(search_key2, search_key1);

DROP TABLE IF EXISTS bus_events;
CREATE TABLE bus_events (
    record_id serial unique,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX idx_bus_where ON bus_events(processing_state, processing_owner, processing_available_date);
CREATE INDEX bus_events_tenant_account_record_id ON bus_events(search_key2, search_key1);

DROP TABLE IF EXISTS bus_events_history;
CREATE TABLE bus_events_history (
    record_id serial unique,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX bus_events_history_tenant_account_record_id ON bus_events_history(search_key2, search_key1);
