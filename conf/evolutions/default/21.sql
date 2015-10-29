# --- !Ups

alter table amt_assignments alter column answer text;

# --- !Downs

alter table amt_assignments alter column answer varchar(2056);
