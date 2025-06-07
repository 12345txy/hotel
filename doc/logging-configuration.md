# 酒店管理系统日志配置文档

## 1. 日志系统概述

本系统采用了**SLF4J + Logback**的日志框架组合，提供了完整的日志记录、存储和管理功能。

### 1.1 技术栈
- **SLF4J**: 日志门面接口
- **Logback**: 日志实现框架
- **Spring Boot**: 自动配置支持
- **MDC**: 多线程上下文支持

### 1.2 核心特性
- ✅ 多级别日志输出 (TRACE, DEBUG, INFO, WARN, ERROR)
- ✅ 分类日志文件存储
- ✅ 日志文件自动滚动和压缩
- ✅ 异步日志输出提升性能
- ✅ 彩色控制台输出
- ✅ 多环境配置支持
- ✅ MDC上下文信息记录

## 2. 日志文件结构

### 2.1 日志目录结构
```
logs/
├── hotel-application.log          # 应用总日志
├── air-conditioner.log           # 空调系统专用日志
├── database.log                  # 数据库操作日志
├── error.log                     # 错误日志
├── performance.log               # 性能监控日志
└── archive/                      # 历史日志存档目录
    ├── hotel-application.2024-01-01.0.log
    ├── air-conditioner.2024-01-01.0.log
    └── ...
```

### 2.2 日志文件说明

| 文件名 | 用途 | 滚动策略 | 保留时间 |
|--------|------|----------|----------|
| `hotel-application.log` | 应用主日志，记录所有业务操作 | 100MB/天 | 30天 |
| `air-conditioner.log` | 空调系统专用日志，记录调度、温控等 | 50MB/天 | 15天 |
| `database.log` | 数据库SQL和参数日志 | 按天 | 7天 |
| `error.log` | 只记录ERROR级别的错误信息 | 按天 | 30天 |
| `performance.log` | 性能监控和方法耗时统计 | 按天 | 7天 |

## 3. 日志级别配置

### 3.1 全局日志级别
```properties
# 根日志级别
logging.level.root=INFO

# 应用包日志级别
logging.level.com.example.hotel=DEBUG

# 第三方框架日志级别
logging.level.org.springframework=INFO
logging.level.org.hibernate=INFO
```

### 3.2 特定组件日志级别

#### 空调系统 (DEBUG级别)
- `com.example.hotel.service.AirConditionerService`
- `com.example.hotel.service.AirConditionerSchedulerService`

#### 控制器层 (INFO级别)
- `com.example.hotel.controller`

#### 数据库操作 (DEBUG级别)
- `org.hibernate.SQL`
- `org.hibernate.type.descriptor.sql.BasicBinder`

## 4. 环境配置

### 4.1 开发环境 (dev)
```xml
<springProfile name="dev">
    <logger name="com.example.hotel" level="DEBUG" />
    <logger name="org.springframework.web" level="DEBUG" />
</springProfile>
```

### 4.2 生产环境 (prod)
```xml
<springProfile name="prod">
    <logger name="com.example.hotel" level="INFO" />
    <logger name="org.springframework" level="WARN" />
    <logger name="org.hibernate" level="WARN" />
</springProfile>
```

### 4.3 测试环境 (test)
```xml
<springProfile name="test">
    <logger name="com.example.hotel" level="DEBUG" />
    <logger name="org.springframework.test" level="DEBUG" />
</springProfile>
```

## 5. 日志格式说明

### 5.1 控制台输出格式
```
%red(%d{yyyy-MM-dd HH:mm:ss}) %green([%thread]) %highlight(%-5level) %boldMagenta(%logger{50}) - %cyan(%msg%n)
```

**示例输出:**
```
2024-01-15 14:30:25 [main] INFO  c.e.hotel.service.AirConditionerService - 空调操作: 创建请求, 房间: 101, 参数: COOLING, HIGH, 22.0
```

### 5.2 文件输出格式
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n
```

**示例输出:**
```
2024-01-15 14:30:25.123 [scheduler-thread-1] INFO  c.e.hotel.service.AirConditionerSchedulerService - 调度操作: 处理等待队列, 参数: [101, 102]
```

## 6. LoggerUtil工具类使用

### 6.1 空调操作日志
```java
// 记录空调请求创建
LoggerUtil.logAirConditionerOperation("创建请求", roomId, null, mode, fanSpeed, targetTemp);

// 记录空调分配
LoggerUtil.logAirConditionerOperation("分配空调", roomId, acId);

// 记录空调释放
LoggerUtil.logAirConditionerOperation("释放空调", roomId, acId, "用户取消");
```

### 6.2 调度操作日志
```java
// 记录调度循环
LoggerUtil.logSchedulerOperation("调度循环开始", waitingQueueSize, serviceSetSize);

// 记录队列处理
LoggerUtil.logSchedulerOperation("处理等待队列", processedCount);
```

### 6.3 API请求日志
```java
// 记录API请求
LoggerUtil.logApiRequest("/api/ac/room/101/request", "POST", requestBody);

// 记录API响应
LoggerUtil.logApiResponse("/api/ac/room/101/request", "POST", 200, responseBody);
```

### 6.4 性能监控
```java
// 开始性能监控
LoggerUtil.startPerformanceMonitor("空调调度算法");

// 执行业务逻辑
performScheduling();

// 结束性能监控
LoggerUtil.endPerformanceMonitor("空调调度算法");
```

### 6.5 温度变化日志
```java
LoggerUtil.logTemperatureChange(roomId, oldTemp, newTemp, "空调制冷");
```

### 6.6 错误日志
```java
try {
    // 业务逻辑
} catch (Exception e) {
    LoggerUtil.logError(AirConditionerService.class, "分配空调", e, roomId, acId);
}
```

## 7. MDC上下文信息

系统自动记录以下MDC上下文信息：

| 键名 | 含义 | 示例值 |
|------|------|--------|
| `operation` | 操作名称 | "创建请求", "分配空调" |
| `roomId` | 房间号 | "101", "102" |
| `acId` | 空调编号 | "1", "2", "3" |
| `endpoint` | API端点 | "/api/ac/room/101/request" |
| `method` | HTTP方法 | "GET", "POST" |
| `statusCode` | 响应状态码 | "200", "400" |
| `duration` | 操作耗时 | "150" (毫秒) |

## 8. 日志监控和告警

### 8.1 性能告警
- 操作耗时超过1秒时自动记录WARNING级别日志
- 可配置监控系统读取performance.log进行告警

### 8.2 错误监控
- 所有ERROR级别日志记录到error.log
- 可配置监控系统实时监控错误日志

### 8.3 关键业务指标
- 空调请求数量统计
- 调度算法执行频率
- 温度变化趋势记录

## 9. 日志最佳实践

### 9.1 日志记录原则
1. **分级记录**: 根据重要性选择合适的日志级别
2. **结构化信息**: 使用MDC记录结构化上下文信息
3. **性能考虑**: 使用异步日志避免影响业务性能
4. **敏感信息**: 避免记录密码等敏感信息

### 9.2 开发建议
1. 在关键业务操作前后记录日志
2. 异常处理时必须记录错误日志
3. 性能敏感操作使用性能监控
4. 使用LoggerUtil工具类统一日志格式

### 9.3 运维建议
1. 定期清理旧日志文件释放磁盘空间
2. 监控日志文件大小，防止磁盘满
3. 配置日志监控系统及时发现问题
4. 根据业务需要调整日志级别和保留策略

## 10. 故障排查

### 10.1 常见问题

**问题1: 日志文件不生成**
- 检查logs目录权限
- 确认logback-spring.xml配置正确
- 查看应用启动日志

**问题2: 日志级别不生效**
- 检查application.properties配置
- 确认环境profile设置
- 验证logback配置优先级

**问题3: 日志文件过大**
- 调整滚动策略配置
- 减少DEBUG级别日志输出
- 增加日志清理频率

### 10.2 调试技巧
1. 临时修改日志级别: 通过actuator端点动态调整
2. 查看特定线程日志: 使用grep过滤thread信息
3. 分析性能日志: 关注duration字段统计

这套日志配置为酒店管理系统提供了完整的日志记录、监控和故障排查能力，支持系统的高效运维和问题定位。 