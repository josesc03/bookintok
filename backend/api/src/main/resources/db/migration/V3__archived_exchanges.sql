-- Each participant can archive an exchange chat for themselves
ALTER TABLE exchanges ADD COLUMN owner_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exchanges ADD COLUMN requester_archived BOOLEAN NOT NULL DEFAULT FALSE;
