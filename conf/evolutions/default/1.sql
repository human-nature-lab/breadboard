# --- !Ups

create table content (
  id                        bigint not null,
  experiment_id             bigint not null,
  name                      varchar(255),
  html                      text,
  constraint pk_content primary key (id))
;

create table data (
  id                        bigint not null,
  name                      varchar(255),
  value                     varchar(255),
  experiment_instance_id    bigint,
  constraint pk_data primary key (id))
;

create table events (
  id                        bigint not null,
  datetime                  timestamp,
  name                      varchar(255),
  experiment_instance_id    bigint,
  constraint pk_events primary key (id))
;

create table event_data (
  id                        bigint not null,
  name                      varchar(255),
  value                     varchar(255),
  event_id                  bigint,
  constraint pk_event_data primary key (id))
;

create table experiments (
  id                        bigint not null,
  name                      varchar(255),
  style                     text,
  constraint pk_experiments primary key (id))
;

create table experiment_instances (
  id                        bigint not null,
  creation_date             timestamp,
  name                      varchar(255),
  experiment_id             bigint,
  status                    varchar(8),
  constraint ck_experiment_instances_status check (status in ('RUNNING','TESTING','STOPPED','FINISHED','ARCHIVED')),
  constraint pk_experiment_instances primary key (id))
;

create table images (
  id                        bigint not null,
  experiment_id             bigint not null,
  file                      blob,
  file_name                 varchar(255),
  content_type              varchar(255),
  constraint pk_images primary key (id))
;

create table parameters (
  id                        bigint not null,
  experiment_id             bigint not null,
  name                      varchar(255),
  type                      varchar(255),
  min_val                   varchar(255),
  max_val                   varchar(255),
  default_val               varchar(255),
  description               varchar(255),
  constraint pk_parameters primary key (id))
;

create table steps (
  id                        bigint not null,
  experiment_id             bigint not null,
  name                      varchar(255),
  source                    text,
  constraint pk_steps primary key (id))
;

create table users (
  email                     varchar(255) not null,
  name                      varchar(255),
  password                  varchar(255),
  uid                       varchar(255),
  selected_experiment_id    bigint,
  current_script            text,
  experiment_instance_id    bigint,
  constraint pk_users primary key (email))
;


create table users_experiments (
  users_email                    varchar(255) not null,
  experiments_id                 bigint not null,
  constraint pk_users_experiments primary key (users_email, experiments_id))
;
create sequence content_seq;

create sequence data_seq;

create sequence events_seq;

create sequence event_data_seq;

create sequence experiments_seq;

create sequence experiment_instances_seq;

create sequence images_seq;

create sequence parameters_seq;

create sequence steps_seq;

create sequence users_seq;

alter table content add constraint fk_content_experiments_1 foreign key (experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_content_experiments_1 on content (experiment_id);
alter table data add constraint fk_data_experimentInstance_2 foreign key (experiment_instance_id) references experiment_instances (id) on delete restrict on update restrict;
create index ix_data_experimentInstance_2 on data (experiment_instance_id);
alter table events add constraint fk_events_experimentInstance_3 foreign key (experiment_instance_id) references experiment_instances (id) on delete restrict on update restrict;
create index ix_events_experimentInstance_3 on events (experiment_instance_id);
alter table event_data add constraint fk_event_data_event_4 foreign key (event_id) references events (id) on delete restrict on update restrict;
create index ix_event_data_event_4 on event_data (event_id);
alter table experiment_instances add constraint fk_experiment_instances_experi_5 foreign key (experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_experiment_instances_experi_5 on experiment_instances (experiment_id);
alter table images add constraint fk_images_experiments_6 foreign key (experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_images_experiments_6 on images (experiment_id);
alter table parameters add constraint fk_parameters_experiments_7 foreign key (experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_parameters_experiments_7 on parameters (experiment_id);
alter table steps add constraint fk_steps_experiments_8 foreign key (experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_steps_experiments_8 on steps (experiment_id);
alter table users add constraint fk_users_selectedExperiment_9 foreign key (selected_experiment_id) references experiments (id) on delete restrict on update restrict;
create index ix_users_selectedExperiment_9 on users (selected_experiment_id);



alter table users_experiments add constraint fk_users_experiments_users_01 foreign key (users_email) references users (email) on delete restrict on update restrict;

alter table users_experiments add constraint fk_users_experiments_experime_02 foreign key (experiments_id) references experiments (id) on delete restrict on update restrict;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists content;

drop table if exists data;

drop table if exists events;

drop table if exists event_data;

drop table if exists experiments;

drop table if exists experiment_instances;

drop table if exists images;

drop table if exists parameters;

drop table if exists steps;

drop table if exists users;

drop table if exists users_experiments;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists content_seq;

drop sequence if exists data_seq;

drop sequence if exists events_seq;

drop sequence if exists event_data_seq;

drop sequence if exists experiments_seq;

drop sequence if exists experiment_instances_seq;

drop sequence if exists images_seq;

drop sequence if exists parameters_seq;

drop sequence if exists steps_seq;

drop sequence if exists users_seq;

