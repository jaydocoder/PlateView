INSERT INTO wechat_passage_senders(sender_username, original_display_name, display_alias, enabled)
VALUES
    ('xurujun9599', '徐如军', '徐站', TRUE),
    ('wxid_2493514935112', '三叔', '三叔', TRUE)
ON CONFLICT(sender_username) DO UPDATE SET
    original_display_name = EXCLUDED.original_display_name,
    display_alias = EXCLUDED.display_alias,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

UPDATE wechat_messages
SET business_type = 'PASSAGE_MESSAGE'
WHERE sender_username IN ('xurujun9599', 'wxid_2493514935112')
  AND business_type = 'GENERAL_MESSAGE'
  AND normalized_content ~ '[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新使领][A-HJ-NP-Z][A-HJ-NP-Z0-9]{5,6}'
  AND raw_content ~ '(通行|放行|予以|允许|前往|去白哈巴|进(入)?(喀纳斯|贾登峪|禾木|白哈巴))';
