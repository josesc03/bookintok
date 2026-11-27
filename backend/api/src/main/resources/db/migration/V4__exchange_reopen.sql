-- A participant of a cancelled exchange can ask to resume it; the other participant must accept
ALTER TABLE exchanges ADD COLUMN reopen_requested_by VARCHAR(128);
