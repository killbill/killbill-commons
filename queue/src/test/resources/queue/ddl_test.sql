/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS dummy;
CREATE TABLE dummy (
    dkey varchar(256) NOT NULL,
    dvalue int(11) unsigned default null,
    PRIMARY KEY(dkey)
);