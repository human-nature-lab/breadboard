# --- !Ups

alter table experiments add column qualification_type_id_sandbox varchar(128);

# --- !Downs

alter table experiments drop column qualification_type_id_sandbox;
