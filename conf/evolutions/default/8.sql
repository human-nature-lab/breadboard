# --- !Ups

alter table amt_assignments add column worker_blocked bit default 0;

# --- !Downs

alter table amt_assignments drop column worker_blocked;
