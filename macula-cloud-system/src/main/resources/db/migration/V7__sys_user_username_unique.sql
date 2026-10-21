-- Login names are global identities; duplicate non-null names must stop this migration.
-- MySQL UNIQUE permits multiple NULL values, which are not usable login names.
ALTER TABLE sys_user
    ADD UNIQUE KEY uk_sys_user_username (username);
