# --- !Ups

alter table amt_assignments add column reason varchar(255);
alter table amt_assignments add column assignment_completed bit default 0;

# --- !Downs

alter table amt_assignments drop column reason;
alter table amt_assignments drop column assignment_completed;
