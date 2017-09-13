# --- !Ups

create table amt_hits (
  id                        bigint not null,
  creation_date             timestamp,
  request_id                varchar(255),
  is_valid                  varchar(255),
  hit_id                    varchar(255),
  title                     varchar(255),
  description               varchar(255),
  lifetime_in_seconds       varchar(255),
  max_assignments           varchar(255),
  external_url              varchar(255),
  reward                    varchar(255),
  experiment_instance_id	bigint not null,
  constraint pk_amt_hits primary key (id)
  );

create sequence amt_hits_seq;

alter table amt_hits add constraint fk_amt_hits_experiment_instance foreign key (experiment_instance_id) references experiment_instances (id) on delete restrict on update restrict;


# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;



drop table if exists amt_hits;



SET REFERENTIAL_INTEGRITY TRUE;



drop sequence if exists amt_hits_seq;

