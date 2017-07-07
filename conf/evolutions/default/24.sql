# --- !Ups

alter table content add column `language` varchar(255);
update content set `language` = 'en' where 1;

# --- !Downs

alter table content drop column language;
