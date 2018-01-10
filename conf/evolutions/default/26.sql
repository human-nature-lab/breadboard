# --- !Ups

alter table users drop column if exists default_language;
alter table users add column default_language_id bigint;
alter table users add constraint fk_default_language_languages foreign key (default_language_id) references languages (id) on delete restrict on update restrict;
create index ix_users_default_language on users (default_language_id);

# --- !Downs

alter table users add column default_language varchar(8) default 'eng';
alter table users drop column if exists default_language_id;
alter table users drop constraint if exists fk_default_language_languages;
drop index if exists ix_users_default_language;
