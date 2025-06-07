-- 检查room表结构
USE hotel_system;

-- 查看room表结构
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE
    TABLE_SCHEMA = 'hotel_system'
    AND TABLE_NAME = 'room'
ORDER BY ORDINAL_POSITION;

-- 查看assigned_ac_id字段是否存在
SELECT COUNT(*) as field_exists
FROM information_schema.COLUMNS
WHERE
    TABLE_SCHEMA = 'hotel_system'
    AND TABLE_NAME = 'room'
    AND COLUMN_NAME = 'assigned_ac_id';

-- 如果字段不存在，创建它
SET
    @sql = (
        SELECT
            CASE
                WHEN COUNT(*) = 0 THEN 'ALTER TABLE room ADD COLUMN assigned_ac_id INT NULL COMMENT ''分配的空调ID，NULL表示未分配空调'';'
                ELSE 'SELECT ''Field assigned_ac_id already exists'' as message;'
            END
        FROM information_schema.COLUMNS
        WHERE
            TABLE_SCHEMA = 'hotel_system'
            AND TABLE_NAME = 'room'
            AND COLUMN_NAME = 'assigned_ac_id'
    );

PREPARE stmt FROM @sql;

EXECUTE stmt;

DEALLOCATE PREPARE stmt;