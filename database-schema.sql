-- 酒店管理系统数据库架构
-- 先设置外键检查为0，避免创建顺序问题
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS hotel_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE hotel_system;

-- 1. 房间表
CREATE TABLE room (
    room_id INT PRIMARY KEY COMMENT '房间号',
    price DECIMAL(10, 2) NOT NULL COMMENT '每日价格',
    initial_temp DECIMAL(5, 2) NOT NULL COMMENT '初始温度',
    current_temp DECIMAL(5, 2) NOT NULL COMMENT '当前温度',
    occupied BOOLEAN DEFAULT FALSE COMMENT '是否被占用',
    check_in_time DATETIME NULL COMMENT '入住时间',
    check_out_time DATETIME NULL COMMENT '退房时间'
) ENGINE = InnoDB COMMENT = '房间信息表';

-- 2. 客人表
CREATE TABLE guest (
    id VARCHAR(50) PRIMARY KEY COMMENT '身份证号',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    phone VARCHAR(20) NOT NULL COMMENT '电话号码'
) ENGINE = InnoDB COMMENT = '客人信息表';

-- 3. 账单表
CREATE TABLE bill (
    bill_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账单ID',
    room_id INT NOT NULL COMMENT '房间号',
    guest_id VARCHAR(50) NOT NULL COMMENT '客人身份证号',
    check_in_time DATETIME NOT NULL COMMENT '入住时间',
    check_out_time DATETIME NULL COMMENT '退房时间',
    days_of_stay INT DEFAULT 0 COMMENT '入住天数',
    total_cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '总费用',
    room_cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '房费',
    ac_cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '空调费用',
    other_cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '其他费用',
    bill_status VARCHAR(20) DEFAULT 'UNPAID' COMMENT '账单状态',
    payment_time DATETIME NULL COMMENT '支付时间'
) ENGINE = InnoDB COMMENT = '账单表';

-- 4. 空调设备表
CREATE TABLE air_conditioner (
    ac_id INT PRIMARY KEY COMMENT '空调ID (1-3)',
    serving_room_id INT NULL COMMENT '当前服务的房间ID',
    on_status BOOLEAN DEFAULT FALSE COMMENT '是否开启',
    mode VARCHAR(10) NULL COMMENT '模式: COOLING, HEATING',
    fan_speed VARCHAR(10) NULL COMMENT '风速: HIGH, MEDIUM, LOW',
    target_temp DECIMAL(5, 2) NULL COMMENT '目标温度',
    current_temp DECIMAL(5, 2) NULL COMMENT '当前温度',
    request_time DATETIME NULL COMMENT '请求时间',
    service_start_time DATETIME NULL COMMENT '服务开始时间',
    service_end_time DATETIME NULL COMMENT '服务结束时间',
    service_duration INT DEFAULT 0 COMMENT '服务时长(分钟)',
    cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '当前费用',
    priority INT DEFAULT 0 COMMENT '优先级',
    service_time INT DEFAULT 0 COMMENT '已服务时间(分钟)'
) ENGINE = InnoDB COMMENT = '空调设备表';

-- 5. 空调请求表
CREATE TABLE air_conditioner_request (
    request_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '请求ID',
    room_id INT NOT NULL COMMENT '房间ID',
    mode VARCHAR(10) NOT NULL COMMENT '模式: COOLING, HEATING',
    fan_speed VARCHAR(10) NOT NULL COMMENT '风速: HIGH, MEDIUM, LOW',
    target_temp DECIMAL(5, 2) NOT NULL COMMENT '目标温度',
    current_room_temp DECIMAL(5, 2) NOT NULL COMMENT '房间当前温度',
    request_time DATETIME NOT NULL COMMENT '请求时间',
    assigned_ac_id INT NULL COMMENT '分配的空调ID',
    priority INT NOT NULL COMMENT '优先级',
    active BOOLEAN DEFAULT TRUE COMMENT '请求是否激活'
) ENGINE = InnoDB COMMENT = '空调请求表';

-- 6. 账单详单表
CREATE TABLE bill_detail (
    detail_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '详单ID',
    bill_id BIGINT NOT NULL COMMENT '关联的账单ID',
    room_id INT NOT NULL COMMENT '房间号',
    ac_id INT NOT NULL COMMENT '空调ID',
    request_time DATETIME NOT NULL COMMENT '请求时间',
    service_start_time DATETIME NULL COMMENT '服务开始时间',
    service_end_time DATETIME NULL COMMENT '服务结束时间',
    service_duration INT DEFAULT 0 COMMENT '服务时长(分钟)',
    fan_speed VARCHAR(10) NOT NULL COMMENT '风速等级',
    mode VARCHAR(10) NOT NULL COMMENT '模式',
    target_temp DECIMAL(5, 2) NOT NULL COMMENT '目标温度',
    temp_change DECIMAL(5, 2) DEFAULT 0.00 COMMENT '温度变化量',
    energy_consumed DECIMAL(8, 2) DEFAULT 0.00 COMMENT '能耗(度)',
    cost DECIMAL(10, 2) DEFAULT 0.00 COMMENT '费用',
    rate DECIMAL(5, 2) NOT NULL DEFAULT 1.0 COMMENT '费率(元/度)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE = InnoDB COMMENT = '账单详单表';

-- 添加外键约束
ALTER TABLE bill
ADD CONSTRAINT fk_bill_room FOREIGN KEY (room_id) REFERENCES room (room_id),
ADD CONSTRAINT fk_bill_guest FOREIGN KEY (guest_id) REFERENCES guest (id);

ALTER TABLE air_conditioner
ADD CONSTRAINT fk_ac_serving_room FOREIGN KEY (serving_room_id) REFERENCES room (room_id);

ALTER TABLE air_conditioner_request
ADD CONSTRAINT fk_request_room FOREIGN KEY (room_id) REFERENCES room (room_id),
ADD CONSTRAINT fk_request_ac FOREIGN KEY (assigned_ac_id) REFERENCES air_conditioner (ac_id);

ALTER TABLE bill_detail
ADD CONSTRAINT fk_detail_bill FOREIGN KEY (bill_id) REFERENCES bill (bill_id) ON DELETE CASCADE,
ADD CONSTRAINT fk_detail_room FOREIGN KEY (room_id) REFERENCES room (room_id),
ADD CONSTRAINT fk_detail_ac FOREIGN KEY (ac_id) REFERENCES air_conditioner (ac_id);

-- 添加索引
CREATE INDEX idx_room_occupied ON room (occupied);

CREATE INDEX idx_guest_phone ON guest (phone);

CREATE INDEX idx_bill_status ON bill (bill_status);

CREATE INDEX idx_bill_room ON bill (room_id);

CREATE INDEX idx_ac_serving ON air_conditioner (serving_room_id);

CREATE INDEX idx_ac_status ON air_conditioner (on_status);

CREATE INDEX idx_request_room ON air_conditioner_request (room_id);

CREATE INDEX idx_request_active ON air_conditioner_request (active);

CREATE INDEX idx_request_ac ON air_conditioner_request (assigned_ac_id);

CREATE INDEX idx_detail_bill ON bill_detail (bill_id);

CREATE INDEX idx_detail_room ON bill_detail (room_id);

CREATE INDEX idx_detail_ac ON bill_detail (ac_id);

-- 初始化数据
INSERT INTO
    room (
        room_id,
        price,
        initial_temp,
        current_temp,
        occupied
    )
VALUES (1, 100.00, 25.0, 25.0, FALSE),
    (2, 120.00, 25.0, 25.0, FALSE),
    (3, 150.00, 25.0, 25.0, FALSE),
    (4, 180.00, 25.0, 25.0, FALSE),
    (5, 200.00, 25.0, 25.0, FALSE);

INSERT INTO
    air_conditioner (
        ac_id,
        serving_room_id,
        on_status
    )
VALUES (1, NULL, FALSE),
    (2, NULL, FALSE),
    (3, NULL, FALSE);

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;