CREATE TABLE chat
(
    chatId      VARCHAR   NOT NULL,
    requestTime TIMESTAMP NOT NULL,
    approveTime TIMESTAMP,
    closeTime   TIMESTAMP,
    userId      VARCHAR,
    employeeId  VARCHAR,
    context     VARCHAR,
    status      VARCHAR   NOT NULL,
    result      VARCHAR
);

CREATE TABLE chatdata
(
    chatId    VARCHAR   NOT NULL,
    time      TIMESTAMP NOT NULL,
    messageId VARCHAR   NOT NULL,
    origin    VARCHAR   NOT NULL,
    actorId   VARCHAR   NOT NULL,
    eventType VARCHAR   NOT NULL,
    eventData VARCHAR
);

CREATE INDEX chat_chatid_idx ON chat USING HASH (chatId);
CREATE INDEX chat_requesttime_idx ON chat USING BTREE (requestTime);
CREATE INDEX chat_approvetime_idx ON chat USING BTREE (approveTime);
CREATE INDEX chat_closetime_idx ON chat USING BTREE (closeTime);
CREATE INDEX chat_userid_idx ON chat USING HASH (userId);
CREATE INDEX chat_context_idx ON chat USING HASH (context);
CREATE INDEX chat_employeeid_idx ON chat USING HASH (employeeId);
CREATE INDEX chat_status_idx ON chat USING HASH (status);

CREATE INDEX chatdata_chatid_idx ON chatdata USING HASH (chatID);
CREATE INDEX chatdata_time_idx ON chatdata USING BTREE (time);
CREATE INDEX chatdata_actorid_idx ON chatdata USING HASH (actorId);
