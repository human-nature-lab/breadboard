# --- !Ups

create table languages (
  id                        bigint not null,
  code                      varchar(8),
  name                      varchar(255),
  constraint pk_language primary key (id)
);

create table experiments_languages (
  experiments_id             bigint not null,
  languages_id               bigint not null,
  foreign key (experiments_id) references experiments(id),
  foreign key (languages_id) references languages(id),
);

create table translations (
  id                        bigint not null,
  html                      text,
  content_id                bigint not null,
  languages_id               bigint not null,
  foreign key (content_id) references content(id),
  foreign key (languages_id) references languages(id),
  constraint pk_translations primary key (id)
);

create sequence languages_seq;

create sequence experiments_languages_seq;

create sequence translations_seq;

alter table content drop column html;

# --- !Downs

drop table if exists languages;

drop table if exists experiment_languages;

drop table if exists translations;

drop sequence if exists languages_seq;

drop sequence if exists experiments_languages_seq;

drop sequence if exists translations_seq;

alter table content add column html text;
