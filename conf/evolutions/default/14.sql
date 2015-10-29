# --- !Ups

alter table amt_hits add column disallow_previous varchar(128);

# --- !Downs

alter table amt_hits drop column disallow_previous;
