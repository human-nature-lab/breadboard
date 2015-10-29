# --- !Ups

alter table amt_assignments add column bonus_granted bit default 0;

# --- !Downs

alter table amt_assignments drop column bonus_granted;
