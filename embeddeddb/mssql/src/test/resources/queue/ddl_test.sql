CREATE SCHEMA killbillg; GO

DROP TABLE IF EXISTS killbillg.dummy; GO
CREATE TABLE killbillg.dummy (
    dkey NVARCHAR(255) NOT NULL,
    dvalue int primary key
);
GO

DROP TABLE IF EXISTS killbillg.demo; GO
CREATE TABLE demo_db.demo (
    demo_key INT PRIMARY KEY ,
    demo_username NVARCHAR(255) NOT NULL ,
    demo_password NVARCHAR(512) NOT NULL ,
    dValue INT NOT NULL ,
    FOREIGN KEY (dValue) REFERENCES killbillg.dummy (dValue)
);
GO