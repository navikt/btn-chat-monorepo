CREATE DATABASE "btn-chat" WITH ENCODING 'UTF8';

CREATE USER "btn-chat-sbs" ENCRYPTED PASSWORD 'btn-chat-sbs' SUPERUSER NOCREATEDB NOCREATEROLE;
CREATE USER "btn-chat-fss" ENCRYPTED PASSWORD 'btn-chat-fss' SUPERUSER NOCREATEDB NOCREATEROLE;

-- For simplicity sake. In Q/P the users have less privileges
CREATE ROLE "btn-chat-sbs-user" SUPERUSER;
CREATE ROLE "btn-chat-fss-user" SUPERUSER;
CREATE ROLE "btn-chat-fss-admin" SUPERUSER;

GRANT "btn-chat-fss-admin" TO "btn-chat-fss";
GRANT "btn-chat-fss-user" TO "btn-chat-fss";
GRANT "btn-chat-sbs-user" TO "btn-chat-sbs";
