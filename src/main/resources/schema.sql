-- 检查并创建 hotel_system 数据库
CREATE DATABASE IF NOT EXISTS hotel_system;
USE hotel_system;

-- 创建房间表（若不存在）
CREATE TABLE IF NOT EXISTS room (
                                    room_id INT PRIMARY KEY,
                                    price DECIMAL(10, 2) NOT NULL,
    initial_temp DECIMAL(5, 2) NOT NULL,
    current_temp DECIMAL(5, 2) NOT NULL,
    occupied BOOLEAN DEFAULT FALSE,
    check_in_time TIMESTAMP,
    check_out_time TIMESTAMP
    );

-- 创建客户表（若不存在）
CREATE TABLE IF NOT EXISTS guest (
                                     id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL
    );

-- 创建入住记录表（若不存在）
CREATE TABLE IF NOT EXISTS checkin_record (
                                              record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                              room_id INT NOT NULL,
                                              guest_id VARCHAR(50) NOT NULL,
    check_in_time TIMESTAMP NOT NULL,
    check_out_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (room_id) REFERENCES room (room_id),
    FOREIGN KEY (guest_id) REFERENCES guest (id)
    );

-- 创建空调表（若不存在）
CREATE TABLE IF NOT EXISTS air_conditioner (
                                               ac_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                               room_id INT NOT NULL UNIQUE,
                                               on_status BOOLEAN DEFAULT FALSE,
                                               mode VARCHAR(10),
    fan_speed VARCHAR(10),
    target_temp DECIMAL(5, 2),
    current_temp DECIMAL(5, 2),
    priority INT,
    service_time INT,
    FOREIGN KEY (room_id) REFERENCES room (room_id)
    );

-- 创建空调使用记录表（若不存在）
CREATE TABLE IF NOT EXISTS ac_usage (
                                        usage_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                        room_id INT NOT NULL,
                                        request_time TIMESTAMP NOT NULL,
                                        service_start_time TIMESTAMP,
                                        service_end_time TIMESTAMP,
                                        service_duration INT,
                                        fan_speed VARCHAR(10) NOT NULL,
    cost DECIMAL(10, 2) DEFAULT 0,
    rate DECIMAL(5, 2) NOT NULL,
    FOREIGN KEY (room_id) REFERENCES room (room_id)
    );

-- 创建账单表（若不存在）
CREATE TABLE IF NOT EXISTS bill (
                                    bill_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                    room_id INT NOT NULL,
                                    check_in_time TIMESTAMP NOT NULL,
                                    check_out_time TIMESTAMP,
                                    days_of_stay INT,                       -- 入住天数
                                    total_cost DECIMAL(10, 2) NOT NULL,
    room_cost DECIMAL(10, 2) NOT NULL,
    ac_cost DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (room_id) REFERENCES room (room_id)
    );