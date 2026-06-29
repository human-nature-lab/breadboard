# --- !Ups

-- file_mode was historically added by Global.version2Point4Upgrade at startup but was
-- never captured as an evolution, so fold it in here. NOTE: Play splits evolution SQL on
-- the semicolon, so comments in this file must not contain one.
alter table experiments add column if not exists file_mode bit default 0;

-- Retire the bespoke breadboard_version table. Migration state is now owned by Play
-- evolutions (the play_evolutions table), so this table has no remaining readers.
drop table if exists breadboard_version;

# --- !Downs

create table breadboard_version (
  version                   varchar(255)
);

alter table experiments drop column if exists file_mode;
