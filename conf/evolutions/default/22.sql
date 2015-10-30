# --- !Ups

alter table experiments add column client_html text;

# --- !Downs

alter table experiments drop column client_html;
