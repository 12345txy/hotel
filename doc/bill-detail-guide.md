# 账单详单功能使用指南

## 概述

账单详单（BillDetail）系统已成功持久化到数据库，提供完整的空调使用记录管理功能。

## 功能特性

### 1. 数据持久化
- ✅ 账单详单实体类已添加完整的JPA注解
- ✅ 数据库表结构已创建，包含外键约束和索引优化
- ✅ 支持与Bill实体的一对多关联关系

### 2. 数据库表结构

```sql
CREATE TABLE bill_detail (
    detail_id BIGINT PRIMARY KEY AUTO_INCREMENT,    -- 详单ID
    bill_id BIGINT NOT NULL,                        -- 关联的账单ID
    room_id INT NOT NULL,                           -- 房间号
    ac_id INT NOT NULL,                             -- 空调ID (1-3)
    request_time DATETIME NOT NULL,                 -- 请求时间
    service_start_time DATETIME NULL,               -- 服务开始时间
    service_end_time DATETIME NULL,                 -- 服务结束时间
    service_duration INT DEFAULT 0,                 -- 服务时长(分钟)
    fan_speed VARCHAR(10) NOT NULL,                 -- 风速等级
    mode VARCHAR(10) NOT NULL,                      -- 模式
    target_temp DECIMAL(5, 2) NOT NULL,             -- 目标温度
    temp_change DECIMAL(5, 2) DEFAULT 0.00,         -- 温度变化量
    energy_consumed DECIMAL(8, 2) DEFAULT 0.00,     -- 能耗(度)
    cost DECIMAL(10, 2) DEFAULT 0.00,               -- 费用
    rate DECIMAL(5, 2) NOT NULL DEFAULT 1.0,        -- 费率(元/度)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 创建时间
);
```

### 3. 核心功能

#### 3.1 创建详单记录
当客人请求空调服务时，系统自动创建详单记录：

```java
BillDetail detail = billingService.createBillDetail(
    billId,           // 账单ID
    roomId,           // 房间号
    acId,             // 空调ID
    requestTime,      // 请求时间
    serviceStartTime, // 服务开始时间
    "HIGH",           // 风速
    "COOLING",        // 模式
    25.0,             // 目标温度
    1.0               // 费率
);
```

#### 3.2 服务结束记录
当空调服务结束时，更新详单信息：

```java
BillDetail updatedDetail = billingService.updateBillDetailServiceEnd(
    detailId,         // 详单ID
    endTime,          // 结束时间
    totalCost,        // 总费用
    energyConsumed    // 能耗
);
```

#### 3.3 查询功能
- 根据账单ID查询详单：`getBillDetailsByBillId(billId)`
- 根据房间号查询详单：`getBillDetailsByRoomId(roomId)`
- 查询活跃服务：`getActiveServicesByRoomId(roomId)`
- 统计费用：`getTotalCostByRoomId(roomId)`
- 统计能耗：`getEnergyConsumedByRoomAndTimeRange(...)`

## REST API接口

### 基础URL
```
http://localhost:8080/api/bill-details
```

### 主要接口

#### 1. 获取房间详单列表
```http
GET /api/bill-details/room/{roomId}
```

#### 2. 获取账单详单列表
```http
GET /api/bill-details/bill/{billId}
```

#### 3. 获取活跃服务详单
```http
GET /api/bill-details/room/{roomId}/active
```

#### 4. 创建新详单
```http
POST /api/bill-details
Content-Type: application/json

{
  "billId": 1,
  "roomId": 101,
  "acId": 1,
  "requestTime": "2024-01-01T10:00:00",
  "serviceStartTime": "2024-01-01T10:05:00",
  "fanSpeed": "HIGH",
  "mode": "COOLING",
  "targetTemp": 25.0,
  "rate": 1.0
}
```

#### 5. 结束服务
```http
PUT /api/bill-details/{detailId}/end-service
Content-Type: application/json

{
  "endTime": "2024-01-01T12:00:00",
  "cost": 5.50,
  "energyConsumed": 2.75
}
```

#### 6. 获取房间总费用
```http
GET /api/bill-details/room/{roomId}/total-cost
```

#### 7. 获取能耗统计
```http
GET /api/bill-details/room/{roomId}/energy-consumed?startTime=2024-01-01T00:00:00&endTime=2024-01-02T00:00:00
```

## 数据库优化

### 索引配置
系统为常用查询场景配置了优化索引：

```sql
-- 基础索引（表创建时自动生成）
INDEX idx_bill_id (bill_id)
INDEX idx_room_id (room_id)  
INDEX idx_ac_id (ac_id)
INDEX idx_service_time (service_start_time, service_end_time)

-- 复合索引（后续优化添加）
INDEX idx_bill_detail_bill_room (bill_id, room_id)
INDEX idx_bill_detail_service (service_start_time, service_end_time)
```

### 外键约束
```sql
-- 账单关联约束（级联删除）
FOREIGN KEY (bill_id) REFERENCES bill (bill_id) ON DELETE CASCADE

-- 房间关联约束（限制删除）
FOREIGN KEY (room_id) REFERENCES room (room_id) ON DELETE RESTRICT
```

## 使用场景

### 1. 实时监控
- 查看房间当前活跃的空调服务
- 实时统计服务时长和费用

### 2. 历史查询
- 查询指定时间段的空调使用记录
- 分析用户使用习惯和偏好

### 3. 费用结算
- 精确计算每次空调服务的费用
- 生成详细的账单明细

### 4. 数据分析
- 统计房间能耗情况
- 分析空调使用效率
- 生成各类使用报表

## 注意事项

1. **事务处理**：所有写操作都使用`@Transactional`注解确保数据一致性
2. **时间管理**：创建时间自动设置，服务时长自动计算
3. **数据验证**：枚举类型确保数据有效性
4. **性能优化**：合理使用索引和分页查询
5. **级联关系**：删除账单时会自动删除相关详单

## 集成建议

1. **与调度器集成**：在空调调度开始/结束时自动创建/更新详单
2. **与计费系统集成**：实时更新费用和能耗数据
3. **与监控系统集成**：提供实时的服务状态查询
4. **与报表系统集成**：提供详细的使用数据分析

通过这个完整的账单详单系统，酒店管理系统现在具备了精确的空调使用记录和费用追踪能力。 