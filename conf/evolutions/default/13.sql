# --- !Ups

alter table amt_assignments add column qualification_assigned bit default 0;

# --- !Downs

alter table amt_assignments drop column qualification_assigned;

