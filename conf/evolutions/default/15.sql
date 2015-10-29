# --- !Ups

alter table experiments add column qualification_type_id varchar(128);

# --- !Downs

alter table experiments drop column qualification_type_id;
