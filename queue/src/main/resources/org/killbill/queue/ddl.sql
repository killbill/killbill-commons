/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(256) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    queue_name char(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token char(36),
    PRIMARY KEY(record_id)
);
CREATE INDEX  `idx_comp_where` ON notifications (`effective_date`, `processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_update` ON notifications (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_get_ready` ON notifications (`effective_date`,`created_date`);
CREATE INDEX notifications_search_keys ON notifications(search_key2, search_key1);

DROP TABLE IF EXISTS notifications_history;
CREATE TABLE notifications_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(256) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    queue_name char(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token char(36),
    PRIMARY KEY(record_id)
);

DROP TABLE IF EXISTS bus_events;
CREATE TABLE bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX  `idx_bus_where` ON bus_events (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX bus_events_search_keys ON bus_events(search_key2, search_key1);

DROP TABLE IF EXISTS bus_events_history;
CREATE TABLE bus_events_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
