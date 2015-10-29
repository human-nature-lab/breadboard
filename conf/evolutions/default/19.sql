# --- !Ups

alter table images add column thumb_file blob;
alter table images add column thumb_file_name varchar(255);

# --- !Downs

alter table images drop column thumb_file;
alter table images drop column thumb_file_name;
