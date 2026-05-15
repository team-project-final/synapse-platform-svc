DELETE FROM refresh_tokens
WHERE id IN (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY user_id
                ORDER BY created_at DESC, id DESC
            ) AS row_number
        FROM refresh_tokens
    ) duplicated
    WHERE duplicated.row_number > 1
);

CREATE UNIQUE INDEX uq_refresh_tokens_user_id ON refresh_tokens(user_id);
