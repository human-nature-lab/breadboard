# --- !Ups

alter table users add column default_language varchar(8) default 'eng';
alter table amt_assignments add column bonus_amount varchar(255);
alter table amt_hits alter column experiment_instance_id set null;

# --- !Downs

alter table users drop column default_language;
alter table amt_assignments drop column bonus_amount;
