# --- !Ups

alter table amt_hits add column sandbox bit;

# --- !Downs

alter table amt_hits drop column sandbox;
