# --- !Ups

create table breadboard_version (
  version                   varchar(255)
);

# --- !Downs

drop table if exists breadboard_version;
