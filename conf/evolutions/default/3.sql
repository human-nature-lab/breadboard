# --- !Ups

create table amt_workers (
  id                        bigint not null,
  creation_date             timestamp,
  worker_id                 varchar(255),
  score                     varchar(255),
  completion                varchar(255),
  amt_hit_id	            bigint not null,
  constraint pk_amt_workers primary key (id)
  );

create sequence amt_workers_seq;

alter table amt_workers add constraint fk_amt_workers_amt_hit foreign key (amt_hit_id) references amt_hits (id) on delete restrict on update restrict;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists amt_workers;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists amt_workers_seq;
