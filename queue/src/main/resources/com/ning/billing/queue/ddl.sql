/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    created_date datetime NOT NULL,
    class_name varchar(256) NOT NULL,
    notification_key varchar(2048) NOT NULL,
    user_token char(36),
    future_user_token char(36),
    creating_owner char(50) NOT NULL,
    effective_date datetime NOT NULL,
    queue_name char(64) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX  `idx_comp_where` ON notifications (`effective_date`, `processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_update` ON notifications (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_get_ready` ON notifications (`effective_date`,`created_date`);
CREATE INDEX notifications_tenant_account_record_id ON notifications(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS claimed_notifications;
CREATE TABLE claimed_notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    notification_record_id int(11) unsigned NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX claimed_notifications_tenant_account_record_id ON claimed_notifications(tenant_record_id, account_record_id);


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
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX  `idx_bus_where` ON bus_events (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX bus_events_tenant_account_record_id ON bus_events(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS claimed_bus_events;
CREATE TABLE claimed_bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    bus_event_id char(36) NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX claimed_bus_events_tenant_account_record_id ON claimed_bus_events(tenant_record_id, account_record_id);
