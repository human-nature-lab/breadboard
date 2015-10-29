# --- !Ups

alter table users add column role varchar(128);

# --- !Downs

alter table users drop column role;
