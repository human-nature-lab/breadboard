# --- !Ups

alter table experiment_instances add column has_started bit default 0;

# --- !Downs

alter table experiment_instances drop column has_started;
