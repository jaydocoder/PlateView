UPDATE client_runtime_policy
SET previous_api_base_url = CASE
        WHEN active_api_base_url = 'https://api.chenxiruyu.dpdns.org/'
            THEN active_api_base_url
        ELSE previous_api_base_url
    END,
    active_api_base_url = CASE
        WHEN active_api_base_url = 'https://api.chenxiruyu.dpdns.org/'
            THEN 'https://api.plateview.top/'
        ELSE active_api_base_url
    END,
    previous_update_base_url = CASE
        WHEN active_update_base_url = 'https://api.chenxiruyu.dpdns.org/updates/'
            THEN active_update_base_url
        ELSE previous_update_base_url
    END,
    active_update_base_url = CASE
        WHEN active_update_base_url = 'https://api.chenxiruyu.dpdns.org/updates/'
            THEN 'https://api.plateview.top/updates/'
        ELSE active_update_base_url
    END,
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE active_api_base_url = 'https://api.chenxiruyu.dpdns.org/'
   OR active_update_base_url = 'https://api.chenxiruyu.dpdns.org/updates/';
