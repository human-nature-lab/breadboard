# --- !Ups

alter table amt_hits add column version int;

# --- !Downs

alter table amt_hits drop column version;
