/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS dummy;
CREATE TABLE dummy (
    dkey varchar(255) NOT NULL,
    dvalue int /*! unsigned */ default null,
    PRIMARY KEY(dkey)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
