-- MAC-3 narrows message delivery to one platform-global provider per channel.
-- The unique-key creation intentionally fails when legacy duplicate channel rows exist.
ALTER TABLE sys_message_provider
  DROP INDEX ux_message_channel_name,
  ADD UNIQUE KEY ux_message_channel (channel);

ALTER TABLE sys_message_template
  ADD COLUMN sender_name varchar(255) DEFAULT NULL AFTER language_code;
