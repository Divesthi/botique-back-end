UPDATE tenant
SET preferences = jsonb_set(
        preferences,
        '{instagram}',
        '{"autoCaption": false}'::jsonb,
        true   -- create the key if it doesn't exist
    )
WHERE preferences -> 'instagram' IS NULL;