# --- !Ups

alter table experiments add column client_graph text;

# --- !Downs

alter table experiments drop column client_graph;
