# --- !Ups

alter table experiment_instances add column version int;

# --- !Downs

alter table experiment_instances drop column version;
