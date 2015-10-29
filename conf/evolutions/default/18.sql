# --- !Ups

alter table amt_hits add column tutorial_time varchar(255);

# --- !Downs

alter table amt_hits drop column tutorial_time;
