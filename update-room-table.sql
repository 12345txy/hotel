-- 更新room表，添加assigned_ac_id字段
-- 执行时间：2025-06-07

USE hotel_system;

-- 添加assigned_ac_id字段到room表
ALTER TABLE room
ADD COLUMN assigned_ac_id INT NULL COMMENT '分配的空调ID，NULL表示未分配空调';

-- 添加外键约束，引用air_conditioner表
ALTER TABLE room
ADD CONSTRAINT fk_room_air_conditioner FOREIGN KEY (assigned_ac_id) REFERENCES air_conditioner (ac_id) ON DELETE SET NULL ON UPDATE CASCADE;

-- 创建索引以提高查询性能
CREATE INDEX idx_room_assigned_ac_id ON room (assigned_ac_id);

-- 验证表结构
DESCRIBE room;

-- 验证外键约束
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = 'hotel_system'
    AND TABLE_NAME = 'room'
    AND REFERENCED_TABLE_NAME IS NOT NULL;