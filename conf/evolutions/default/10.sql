# --- !Ups

alter table amt_hits add column extended bit default 0;

# --- !Downs

alter table amt_hits drop column extended;
