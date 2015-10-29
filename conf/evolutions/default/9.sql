# --- !Ups

alter table amt_hits alter column description varchar(1024);

# --- !Downs

alter table amt_hits alter column description varchar(255);
