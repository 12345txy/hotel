# 酒店管理系统数据库架构文档

## 📊 数据库概览

**数据库名称**: `hotel_system`  
**字符集**: `utf8mb4`  
**排序规则**: `utf8mb4_unicode_ci`  
**存储引擎**: `InnoDB`

## 🏗️ 数据库设计理念

### 设计原则
1. **标准化**: 遵循第三范式，减少数据冗余
2. **性能优化**: 合理建立索引，优化查询性能
3. **扩展性**: 预留扩展字段，支持业务增长
4. **完整性**: 外键约束保证数据一致性
5. **可追溯**: 记录创建时间和更新时间

### 核心业务流程
1. **房间管理** → **客人入住** → **空调服务** → **费用结算** → **客人退房**

## 📋 数据表结构

### 1. 房间表 (room)
**用途**: 存储酒店房间的基本信息和状态

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| room_id | INT | PRIMARY KEY | 房间号 |
| price | DECIMAL(10,2) | NOT NULL | 每日价格(元) |
| initial_temp | DECIMAL(5,2) | NOT NULL | 初始温度(°C) |
| current_temp | DECIMAL(5,2) | NOT NULL | 当前温度(°C) |
| occupied | BOOLEAN | DEFAULT FALSE | 是否被占用 |
| check_in_time | DATETIME | NULL | 入住时间 |
| check_out_time | DATETIME | NULL | 退房时间 |
| created_at | TIMESTAMP | AUTO | 创建时间 |
| updated_at | TIMESTAMP | AUTO UPDATE | 更新时间 |

**特点**:
- 房间号作为主键，不使用自增ID
- 温度字段精确到小数点后2位
- 支持动态价格调整

### 2. 客人表 (guest)
**用途**: 存储客人的基本信息

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| id | VARCHAR(50) | PRIMARY KEY | 身份证号 |
| name | VARCHAR(100) | NOT NULL | 姓名 |
| phone | VARCHAR(20) | NOT NULL | 电话号码 |
| created_at | TIMESTAMP | AUTO | 创建时间 |
| updated_at | TIMESTAMP | AUTO UPDATE | 更新时间 |

**索引**:
- `idx_phone`: 电话号码索引
- `idx_name`: 姓名索引

### 3. 入住记录表 (checkin_record)
**用途**: 记录客人的入住和退房信息

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| record_id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 记录ID |
| room_id | INT | NOT NULL, FK | 房间号 |
| guest_id | VARCHAR(50) | NOT NULL, FK | 客人身份证号 |
| check_in_time | DATETIME | NOT NULL | 入住时间 |
| check_out_time | DATETIME | NULL | 退房时间 |
| stay_days | INT | DEFAULT 0 | 入住天数 |
| status | VARCHAR(20) | DEFAULT 'ACTIVE' | 状态 |
| created_at | TIMESTAMP | AUTO | 创建时间 |
| updated_at | TIMESTAMP | AUTO UPDATE | 更新时间 |

**状态值**:
- `ACTIVE`: 当前入住
- `CHECKED_OUT`: 已退房

### 4. 空调设备表 (air_conditioner)
**用途**: 存储空调设备的状态和配置信息

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| ac_id | INT | PRIMARY KEY | 空调ID (1-3) |
| serving_room_id | INT | NULL, FK | 当前服务的房间ID |
| on_status | BOOLEAN | DEFAULT FALSE | 是否开启 |
| mode | VARCHAR(10) | NULL | 模式: COOLING, HEATING |
| fan_speed | VARCHAR(10) | NULL | 风速: HIGH, MEDIUM, LOW |
| target_temp | DECIMAL(5,2) | NULL | 目标温度 |
| current_temp | DECIMAL(5,2) | NULL | 当前温度 |
| request_time | DATETIME | NULL | 请求时间 |
| service_start_time | DATETIME | NULL | 服务开始时间 |
| service_end_time | DATETIME | NULL | 服务结束时间 |
| service_duration | INT | DEFAULT 0 | 服务时长(分钟) |
| cost | DECIMAL(10,2) | DEFAULT 0.00 | 当前费用 |
| priority | INT | DEFAULT 0 | 优先级 |
| service_time | INT | DEFAULT 0 | 已服务时间(分钟) |

### 5. 空调请求表 (ac_request)
**用途**: 记录房间对空调服务的请求

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| request_id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 请求ID |
| room_id | INT | NOT NULL, FK | 房间号 |
| mode | VARCHAR(10) | NOT NULL | 模式 |
| fan_speed | VARCHAR(10) | NOT NULL | 风速 |
| target_temp | DECIMAL(5,2) | NOT NULL | 目标温度 |
| current_room_temp | DECIMAL(5,2) | NOT NULL | 房间当前温度 |
| request_time | DATETIME | NOT NULL | 请求时间 |
| assigned_ac_id | INT | NULL, FK | 分配的空调ID |
| priority | INT | NOT NULL | 优先级 |
| active | BOOLEAN | DEFAULT TRUE | 请求是否激活 |

### 6. 空调使用记录表 (ac_usage)
**用途**: 详细记录空调的使用情况，用于计费

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| usage_id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 使用记录ID |
| room_id | INT | NOT NULL, FK | 房间号 |
| ac_id | INT | NOT NULL, FK | 空调ID |
| request_time | DATETIME | NOT NULL | 请求时间 |
| service_start_time | DATETIME | NULL | 服务开始时间 |
| service_end_time | DATETIME | NULL | 服务结束时间 |
| service_duration | INT | DEFAULT 0 | 服务时长(分钟) |
| fan_speed | VARCHAR(10) | NOT NULL | 风速等级 |
| cost | DECIMAL(10,2) | DEFAULT 0.00 | 费用 |
| rate | DECIMAL(5,2) | NOT NULL | 费率(元/度) |
| temp_change | DECIMAL(5,2) | DEFAULT 0.00 | 温度变化量 |
| energy_consumed | DECIMAL(8,2) | DEFAULT 0.00 | 能耗(度) |

### 7. 账单表 (bill)
**用途**: 生成和管理客人的账单信息

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| bill_id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 账单ID |
| room_id | INT | NOT NULL, FK | 房间号 |
| guest_id | VARCHAR(50) | NOT NULL, FK | 客人身份证号 |
| check_in_time | DATETIME | NOT NULL | 入住时间 |
| check_out_time | DATETIME | NULL | 退房时间 |
| days_of_stay | INT | DEFAULT 0 | 入住天数 |
| total_cost | DECIMAL(10,2) | DEFAULT 0.00 | 总费用 |
| room_cost | DECIMAL(10,2) | DEFAULT 0.00 | 房费 |
| ac_cost | DECIMAL(10,2) | DEFAULT 0.00 | 空调费用 |
| other_cost | DECIMAL(10,2) | DEFAULT 0.00 | 其他费用 |
| bill_status | VARCHAR(20) | DEFAULT 'UNPAID' | 账单状态 |
| payment_time | DATETIME | NULL | 支付时间 |

**账单状态**:
- `UNPAID`: 未支付
- `PAID`: 已支付
- `CANCELLED`: 已取消

### 8. 温度变化记录表 (temperature_log)
**用途**: 记录房间温度的变化历史，便于分析和监控

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| log_id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 记录ID |
| room_id | INT | NOT NULL, FK | 房间号 |
| old_temp | DECIMAL(5,2) | NOT NULL | 变化前温度 |
| new_temp | DECIMAL(5,2) | NOT NULL | 变化后温度 |
| temp_change | DECIMAL(5,2) | STORED COMPUTED | 温度变化量 |
| change_reason | VARCHAR(100) | NULL | 变化原因 |
| ac_id | INT | NULL, FK | 相关空调ID |
| log_time | DATETIME | DEFAULT NOW() | 记录时间 |

### 9. 系统配置表 (system_config)
**用途**: 存储系统的配置参数，支持动态配置

| 字段名 | 数据类型 | 约束 | 说明 |
|--------|----------|------|------|
| config_id | INT | PRIMARY KEY AUTO_INCREMENT | 配置ID |
| config_key | VARCHAR(100) | NOT NULL UNIQUE | 配置键 |
| config_value | VARCHAR(500) | NOT NULL | 配置值 |
| config_desc | VARCHAR(200) | NULL | 配置描述 |
| config_type | VARCHAR(20) | DEFAULT 'STRING' | 配置类型 |

**预设配置项**:
- `ac.price.rate`: 空调费用费率
- `ac.temp.cooling.min/max`: 制冷模式温度范围
- `ac.temp.heating.min/max`: 制热模式温度范围
- `scheduler.time.slice`: 调度时间片
- `temperature.recovery.rate`: 温度回归速率

## 📊 数据库视图

### 1. 房间状态视图 (room_status_view)
**用途**: 提供房间的完整状态信息，包括客人和空调信息

```sql
SELECT 
    r.room_id, r.price, r.current_temp, r.occupied,
    g.name AS guest_name, g.phone AS guest_phone,
    ac.ac_id AS assigned_ac_id, ac.mode AS ac_mode
FROM room r
LEFT JOIN checkin_record cr ON r.room_id = cr.room_id AND cr.status = 'ACTIVE'
LEFT JOIN guest g ON cr.guest_id = g.id
LEFT JOIN air_conditioner ac ON r.room_id = ac.serving_room_id
```

### 2. 空调使用统计视图 (ac_usage_stats)
**用途**: 按房间和日期统计空调使用情况

### 3. 每日收入统计视图 (daily_revenue)
**用途**: 统计每日的收入情况

## ⚙️ 存储过程

### CleanHistoryData(days_to_keep INT)
**用途**: 清理历史数据，释放存储空间
- 清理温度记录
- 清理空调使用记录
- 清理已支付账单记录

## 🔧 触发器

### 1. room_temp_change_log
**触发条件**: 房间温度更新后
**功能**: 自动记录温度变化到 `temperature_log` 表

### 2. bill_auto_calculate
**触发条件**: 插入账单前
**功能**: 自动计算房费、空调费用和总费用

## 📈 索引策略

### 主要索引
- **主键索引**: 所有表的主键自动创建聚集索引
- **外键索引**: 外键字段自动创建索引
- **业务索引**: 根据查询频率创建的业务相关索引

### 复合索引
- `idx_checkin_room_status`: 入住记录的房间号和状态
- `idx_ac_usage_room_time`: 空调使用记录的房间号和时间
- `idx_bill_guest_status`: 账单的客人和状态

## 🔒 数据完整性

### 外键约束
- 严格的引用完整性约束
- 级联删除和更新策略
- 防止孤立数据产生

### 数据类型约束
- 温度数据精度控制
- 枚举值约束（状态、模式等）
- 非空约束和默认值

## 📊 数据初始化

### 基础数据
- **5个房间**: 不同价格和初始温度
- **3台空调**: 初始状态为关闭
- **系统配置**: 完整的配置参数

### 使用建议
1. **开发环境**: 直接执行 `schema.sql` 完整初始化
2. **生产环境**: 分步执行，先创建表结构，再导入数据
3. **数据迁移**: 使用存储过程进行数据清理和维护

这个数据库设计完全匹配项目的业务需求，支持复杂的空调调度算法和详细的费用计算，同时提供了良好的扩展性和性能优化。 