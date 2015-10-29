# --- !Ups

create table amt_assignments (
  id                        bigint not null,
  assignment_id             varchar(255),
  worker_id                 varchar(255),
  assignment_status         varchar(255),
  auto_approval_time        varchar(255),
  accept_time               varchar(255),
  submit_time               varchar(255),
  answer                    varchar(2056),
  score                     varchar(255),
  completion                varchar(255),
  amt_hit_id	            bigint not null,
  constraint pk_amt_assignments primary key (id)
  );

create sequence amt_assignments_seq;

alter table amt_assignments add constraint fk_amt_assignments_amt_hit foreign key (amt_hit_id) references amt_hits (id) on delete restrict on update restrict;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists amt_assignments;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists amt_assignments_seq;
