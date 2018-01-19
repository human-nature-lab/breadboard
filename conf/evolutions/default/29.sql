# --- !Ups

create table messages (
  id                        bigint not null,
  message_uid               varchar(36),
  message_title             varchar(255),
  message_html              text,
  priority                  tinyint,
  auto_open                 bit,
  created_at                timestamp,
  dismissed_at              timestamp
);

# --- !Downs

drop table if exists messages;
