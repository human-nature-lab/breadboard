# --- !Ups

CREATE TABLE CONTENT(
    ID IDENTITY NOT NULL,
    EXPERIMENT_ID BIGINT NOT NULL,
    NAME VARCHAR(255),
    HTML VARCHAR(2147483647)
);         
CREATE INDEX IX_CONTENT_EXPERIMENTS_1 ON CONTENT(EXPERIMENT_ID);
CREATE SEQUENCE CONTENT_SEQ;

CREATE TABLE DATA(
    ID IDENTITY NOT NULL,
    NAME VARCHAR(255),
    VALUE VARCHAR(2147483647),
    EXPERIMENT_INSTANCE_ID BIGINT
);
CREATE INDEX IX_DATA_EXPERIMENTINSTANCE_2 ON DATA(EXPERIMENT_INSTANCE_ID);
CREATE SEQUENCE DATA_SEQ;

CREATE TABLE EVENTS(
    ID IDENTITY NOT NULL SELECTIVITY 100,
    DATETIME TIMESTAMP SELECTIVITY 95,
    NAME VARCHAR(255) SELECTIVITY 1,
    EXPERIMENT_INSTANCE_ID BIGINT SELECTIVITY 1
);      
CREATE INDEX IX_EVENTS_EXPERIMENTINSTANCE_3 ON EVENTS(EXPERIMENT_INSTANCE_ID);
CREATE SEQUENCE EVENTS_SEQ;

CREATE TABLE EVENT_DATA(
    ID IDENTITY NOT NULL SELECTIVITY 100,
    NAME VARCHAR(255) SELECTIVITY 2,
    VALUE VARCHAR(255) SELECTIVITY 8,
    EVENT_ID BIGINT SELECTIVITY 44
);
CREATE INDEX IX_EVENT_DATA_EVENT_4 ON EVENT_DATA(EVENT_ID);
CREATE SEQUENCE EVENT_DATA_SEQ;

CREATE TABLE BREADBOARD_VERSION(
    VERSION VARCHAR(255)
);     
INSERT INTO BREADBOARD_VERSION(VERSION) VALUES ('v2.4.0');

CREATE TABLE EXPERIMENTS(
    ID IDENTITY NOT NULL,
    NAME VARCHAR(255),
    QUALIFICATION_TYPE_ID VARCHAR(128),
    QUALIFICATION_TYPE_ID_SANDBOX VARCHAR(128),
    UID VARCHAR(255),
    FILE_MODE BIT DEFAULT 0
);
CREATE SEQUENCE EXPERIMENTS_SEQ;

CREATE TABLE CUSTOM_FILES(
    ID IDENTITY NOT NULL,
    EXPERIMENT_ID BIGINT NOT NULL,
    NAME VARCHAR(255),
    FILE_NAME VARCHAR(255),
    CONTENT VARCHAR(2147483647),
    MIME_TYPE VARCHAR(255),
    TEMPLATE VARCHAR(255)
);
CREATE INDEX IX_CUSTOM_FILES_EXPERIMENTS ON CUSTOM_FILES(EXPERIMENT_ID);
CREATE SEQUENCE CUSTOM_FILES_SEQ;

CREATE TABLE IMAGES(
    ID IDENTITY NOT NULL,
    EXPERIMENT_ID BIGINT NOT NULL,
    FILE BLOB,
    FILE_NAME VARCHAR(255),
    CONTENT_TYPE VARCHAR(255),
    THUMB_FILE BLOB,
    THUMB_FILE_NAME VARCHAR(255)
);               
CREATE INDEX IX_IMAGES_EXPERIMENTS_6 ON IMAGES(EXPERIMENT_ID);
CREATE SEQUENCE IMAGES_SEQ;

CREATE TABLE PARAMETERS(
    ID IDENTITY NOT NULL,
    EXPERIMENT_ID BIGINT NOT NULL,
    NAME VARCHAR(255),
    TYPE VARCHAR(255),
    MIN_VAL VARCHAR(255),
    MAX_VAL VARCHAR(255),
    DEFAULT_VAL VARCHAR(255),
    DESCRIPTION VARCHAR(255)
);              
CREATE INDEX IX_PARAMETERS_EXPERIMENTS_7 ON PARAMETERS(EXPERIMENT_ID);
CREATE SEQUENCE PARAMETERS_SEQ;

CREATE TABLE STEPS(
    ID IDENTITY NOT NULL,
    EXPERIMENT_ID BIGINT NOT NULL,
    NAME VARCHAR(255),
    SOURCE VARCHAR(2147483647)
);
CREATE INDEX IX_STEPS_EXPERIMENTS_8 ON STEPS(EXPERIMENT_ID);
CREATE SEQUENCE STEPS_SEQ;

CREATE TABLE USERS_EXPERIMENTS(
    USERS_EMAIL VARCHAR(255) NOT NULL,
    EXPERIMENTS_ID BIGINT NOT NULL
);     

CREATE TABLE LANGUAGES(
    ID IDENTITY NOT NULL,
    CODE VARCHAR(8),
    NAME VARCHAR(255)
);
CREATE SEQUENCE LANGUAGES_SEQ;

CREATE TABLE AMT_HITS(
    ID IDENTITY NOT NULL,
    CREATION_DATE TIMESTAMP,
    REQUEST_ID VARCHAR(255),
    IS_VALID VARCHAR(255),
    HIT_ID VARCHAR(255),
    TITLE VARCHAR(255),
    DESCRIPTION VARCHAR(1024),
    LIFETIME_IN_SECONDS VARCHAR(255),
    MAX_ASSIGNMENTS VARCHAR(255),
    EXTERNAL_URL VARCHAR(255),
    REWARD VARCHAR(255),
    EXPERIMENT_INSTANCE_ID BIGINT,
    SANDBOX BIT,
    EXTENDED BIT DEFAULT 0,
    VERSION INT,
    DISALLOW_PREVIOUS VARCHAR(128),
    TUTORIAL_TIME VARCHAR(255)
);
CREATE SEQUENCE AMT_HITS_SEQ;

CREATE TABLE AMT_WORKERS(
    ID IDENTITY NOT NULL,
    CREATION_DATE TIMESTAMP,
    WORKER_ID VARCHAR(255),
    SCORE VARCHAR(255),
    COMPLETION VARCHAR(255),
    AMT_HIT_ID BIGINT NOT NULL
);
CREATE SEQUENCE AMT_WORKERS_SEQ;

CREATE TABLE EXPERIMENT_INSTANCES(
    ID IDENTITY NOT NULL,
    CREATION_DATE TIMESTAMP,
    NAME VARCHAR(255),
    EXPERIMENT_ID BIGINT,
    STATUS VARCHAR(8),
    VERSION INT,
    HAS_STARTED BIT DEFAULT 0
);
CREATE INDEX IX_EXPERIMENT_INSTANCES_EXPERI_5 ON EXPERIMENT_INSTANCES(EXPERIMENT_ID);
CREATE SEQUENCE EXPERIMENT_INSTANCES_SEQ;

CREATE TABLE AMT_ASSIGNMENTS(
    ID IDENTITY NOT NULL,
    ASSIGNMENT_ID VARCHAR(255),
    WORKER_ID VARCHAR(255),
    ASSIGNMENT_STATUS VARCHAR(255),
    AUTO_APPROVAL_TIME VARCHAR(255),
    ACCEPT_TIME VARCHAR(255),
    SUBMIT_TIME VARCHAR(255),
    ANSWER VARCHAR(2147483647),
    SCORE VARCHAR(255),
    COMPLETION VARCHAR(255),
    AMT_HIT_ID BIGINT NOT NULL,
    BONUS_GRANTED BIT DEFAULT 0,
    WORKER_BLOCKED BIT DEFAULT 0,
    QUALIFICATION_ASSIGNED BIT DEFAULT 0,
    REASON VARCHAR(255),
    ASSIGNMENT_COMPLETED BIT DEFAULT 0,
    BONUS_AMOUNT VARCHAR(255)
);
CREATE SEQUENCE AMT_ASSIGNMENTS_SEQ;

CREATE TABLE EXPERIMENTS_LANGUAGES(
    EXPERIMENTS_ID BIGINT NOT NULL,
    LANGUAGES_ID BIGINT NOT NULL
);

CREATE TABLE TRANSLATIONS(
    ID IDENTITY NOT NULL,
    HTML VARCHAR(2147483647),
    CONTENT_ID BIGINT NOT NULL,
    LANGUAGES_ID BIGINT NOT NULL
);
CREATE SEQUENCE TRANSLATIONS_SEQ;

CREATE TABLE USERS(
    EMAIL VARCHAR(255) NOT NULL,
    NAME VARCHAR(255),
    PASSWORD VARCHAR(255),
    UID VARCHAR(255),
    SELECTED_EXPERIMENT_ID BIGINT,
    CURRENT_SCRIPT VARCHAR(2147483647),
    EXPERIMENT_INSTANCE_ID BIGINT,
    ROLE VARCHAR(128),
    DEFAULT_LANGUAGE_ID BIGINT
);             
ALTER TABLE USERS ADD CONSTRAINT PK_USERS PRIMARY KEY(EMAIL);
CREATE INDEX IX_USERS_DEFAULT_LANGUAGE ON USERS(DEFAULT_LANGUAGE_ID);
CREATE INDEX IX_USERS_SELECTEDEXPERIMENT_9 ON USERS(SELECTED_EXPERIMENT_ID);

ALTER TABLE EXPERIMENT_INSTANCES ADD CONSTRAINT CK_EXPERIMENT_INSTANCES_STATUS CHECK(STATUS IN('RUNNING', 'TESTING', 'STOPPED', 'FINISHED', 'ARCHIVED'));
ALTER TABLE EXPERIMENT_INSTANCES ADD CONSTRAINT FK_EXPERIMENTINSTANCES_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE TRANSLATIONS ADD CONSTRAINT FK_TRANSLATIONS_CONTENT FOREIGN KEY(CONTENT_ID) REFERENCES CONTENT(ID);
ALTER TABLE TRANSLATIONS ADD CONSTRAINT FK_TRANSLATIONS_LANGUAGES FOREIGN KEY(LANGUAGES_ID) REFERENCES LANGUAGES(ID);
ALTER TABLE USERS_EXPERIMENTS ADD CONSTRAINT FK_USERSEXPERIMENTS_EXPERIMENTS FOREIGN KEY(EXPERIMENTS_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE EVENT_DATA ADD CONSTRAINT FK_EVENTDATA_EVENTS FOREIGN KEY(EVENT_ID) REFERENCES EVENTS(ID);
ALTER TABLE EXPERIMENTS_LANGUAGES ADD CONSTRAINT FK_EXPERIMENTSLANGUAGES_EXPERIMENTS FOREIGN KEY(EXPERIMENTS_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE USERS ADD CONSTRAINT FK_USERS_LANGUAGES FOREIGN KEY(DEFAULT_LANGUAGE_ID) REFERENCES LANGUAGES(ID);
ALTER TABLE EVENTS ADD CONSTRAINT FK_EVENTS_EXPERIMENTINSTANCES FOREIGN KEY(EXPERIMENT_INSTANCE_ID) REFERENCES EXPERIMENT_INSTANCES(ID);
ALTER TABLE CONTENT ADD CONSTRAINT FK_CONTENT_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE AMT_ASSIGNMENTS ADD CONSTRAINT FK_AMTASSIGNMENTS_AMTHITS FOREIGN KEY(AMT_HIT_ID) REFERENCES AMT_HITS(ID);
ALTER TABLE IMAGES ADD CONSTRAINT FK_IMAGES_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE AMT_WORKERS ADD CONSTRAINT FK_AMTWORKERS_AMTHITS FOREIGN KEY(AMT_HIT_ID) REFERENCES AMT_HITS(ID);
ALTER TABLE EXPERIMENTS_LANGUAGES ADD CONSTRAINT FK_EXPERIMENTSLANGUAGES_LANGUAGES FOREIGN KEY(LANGUAGES_ID) REFERENCES LANGUAGES(ID);
ALTER TABLE USERS ADD CONSTRAINT FK_USERS_EXPERIMENTS FOREIGN KEY(SELECTED_EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE AMT_HITS ADD CONSTRAINT FK_AMTHITS_EXPERIMENTINSTANCES FOREIGN KEY(EXPERIMENT_INSTANCE_ID) REFERENCES EXPERIMENT_INSTANCES(ID);
ALTER TABLE STEPS ADD CONSTRAINT FK_STEPS_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE USERS_EXPERIMENTS ADD CONSTRAINT FK_USERSEXPERIMENTS_USERS FOREIGN KEY(USERS_EMAIL) REFERENCES USERS(EMAIL);
ALTER TABLE PARAMETERS ADD CONSTRAINT FK_PARAMETERS_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);
ALTER TABLE DATA ADD CONSTRAINT FK_DATA_EXPERIMENTINSTANCES FOREIGN KEY(EXPERIMENT_INSTANCE_ID) REFERENCES EXPERIMENT_INSTANCES(ID);
ALTER TABLE CUSTOM_FILES ADD CONSTRAINT FK_CUSTOMFILES_EXPERIMENTS FOREIGN KEY(EXPERIMENT_ID) REFERENCES EXPERIMENTS(ID);

# --- !Downs

DROP TABLE CONTENT CASCADE;
DROP TABLE DATA CASCADE;
DROP TABLE EVENTS CASCADE;
DROP TABLE EVENT_DATA CASCADE;
DROP TABLE BREADBOARD_VERSION CASCADE;
DROP TABLE EXPERIMENTS CASCADE;
DROP TABLE CUSTOM_FILES CASCADE;
DROP TABLE IMAGES CASCADE;
DROP TABLE PARAMETERS CASCADE;
DROP TABLE STEPS CASCADE;
DROP TABLE USERS_EXPERIMENTS CASCADE;
DROP TABLE LANGUAGES CASCADE;
DROP TABLE AMT_HITS CASCADE;
DROP TABLE AMT_WORKERS CASCADE;
DROP TABLE EXPERIMENT_INSTANCES CASCADE;
DROP TABLE AMT_ASSIGNMENTS CASCADE;
DROP TABLE EXPERIMENTS_LANGUAGES CASCADE;
DROP TABLE TRANSLATIONS CASCADE;
DROP TABLE USERS CASCADE;

DROP SEQUENCE CONTENT_SEQ;
DROP SEQUENCE DATA_SEQ;
DROP SEQUENCE EVENTS_SEQ;
DROP SEQUENCE EVENT_DATA_SEQ;
DROP SEQUENCE EXPERIMENTS_SEQ;
DROP SEQUENCE CUSTOM_FILES_SEQ;
DROP SEQUENCE IMAGES_SEQ;
DROP SEQUENCE PARAMETERS_SEQ;
DROP SEQUENCE STEPS_SEQ;
DROP SEQUENCE LANGUAGES_SEQ;
DROP SEQUENCE AMT_HITS_SEQ;
DROP SEQUENCE AMT_WORKERS_SEQ;
DROP SEQUENCE EXPERIMENT_INSTANCES_SEQ;
DROP SEQUENCE AMT_ASSIGNMENTS_SEQ;
DROP SEQUENCE TRANSLATIONS_SEQ;

