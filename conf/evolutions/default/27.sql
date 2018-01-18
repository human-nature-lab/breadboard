# --- !Ups

alter table experiments add column uid varchar(255);

# --- !Downs

alter table experiments drop column uid;
