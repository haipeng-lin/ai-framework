# Human-in-the-Loop Agent Loop Module

## 概述

`SA04-springai-alibaba-agent-loop` 是一个基于 Spring AI Alibaba 的人工介入（Human-in-the-Loop）演示模块。该模块展示了如何让 Agent 自主决策，并通过 HITL Hook 实现人工监督机制。

## 功能特性

- **自然语言交互**：用户用自然语言描述需求，Agent 自主决定是否调用调光工具
- **水族灯调光工具**：虚拟的水族箱灯光控制工具，支持多种预设模式
- **人工介入机制**：通过 HITL Hook 暂停执行并等待人工决策
- **Redis 状态存储**：使用 Redis 存储 traceId 和调光状态信息

## 技术栈

- Spring Boot 3.5.9
- Spring AI Alibaba Agent Framework
- Redis (Redisson)
- DashScope Chat Model (qwen-plus)

## API 接口

### 1. 自然语言对话（触发调光）

```
GET /api/aquarium-light/chat
```

**参数：**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| message | String | 是 | 自然语言描述，如"帮我把主灯调暗一点" |
| userId | String | 否 | 用户 ID |

**响应示例（需要确认）：**
```json
{
  "success": true,
  "traceId": "a1b2c3d4e5f6",
  "threadId": "thread-a1b2c3d4e5f6",
  "message": "Agent 决定执行调光操作，需要人工确认",
  "status": "PENDING_CONFIRMATION",
  "interrupted": true,
  "pendingTool": "aquarium_light_control"
}
```

**响应示例（无需确认）：**
```json
{
  "success": true,
  "traceId": "a1b2c3d4e5f6",
  "threadId": "thread-a1b2c3d4e5f6",
  "message": "好的，我已经把灯调暗了",
  "status": "COMPLETED",
  "interrupted": false
}
```

### 2. 确认调光请求

```
POST /api/aquarium-light/confirm
```

**参数：**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| traceId | String | 是 | 对话返回的 traceId |
| approved | Boolean | 是 | 是否批准（true/false） |
| feedback | String | 否 | 反馈信息 |

**响应示例（批准）：**
```json
{
  "success": true,
  "traceId": "a1b2c3d4e5f6",
  "status": "APPROVED",
  "message": "调光操作已批准，aquarium-1 已设置为 日落 模式，亮度 30%"
}
```

### 3. 查询状态

```
GET /api/aquarium-light/status/{traceId}
```

## 工作流程

1. **发送自然语言**：用户调用 `/chat` 接口，发送如"帮我把水族灯调成日落模式，亮度50%"
2. **Agent 自主决策**：ReactAgent 分析请求，决定是否调用 `aquarium_light_control` 工具
3. **触发中断**：如果调用调光工具，HITL Hook 触发中断，生成 traceId 并保存到 Redis
4. **人工确认**：管理员调用 `/confirm` 接口批准或拒绝
5. **恢复执行**：Agent 根据决策继续执行或返回结果

## 使用示例

### 使用 curl

```bash
# 1. 自然语言对话（让 Agent 决定是否调光）
curl "http://localhost:10040/api/aquarium-light/chat?message=帮我把主灯调成日落模式"

# 2. 确认调光（从响应中获取 traceId）
curl -X POST "http://localhost:10040/api/aquarium-light/confirm?traceId=<traceId>&approved=true"

# 3. 查询状态
curl "http://localhost:10040/api/aquarium-light/status/<traceId>"
```

## Agent 系统提示词（参考）

```
你是一个智能水族箱助手，负责管理水族箱的灯光。
支持的灯光预设模式：日出、日落、夜间、珊瑚生长、全开
亮度范围：0-100%

当用户请求调整灯光时，你可以调用 aquarium_light_control 工具。
如果用户的请求不涉及灯光调整，请直接回复。
```

## 配置

在 `application.yml` 中配置 Redis 连接：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

## 依赖环境

- JDK 17+
- Redis Server
- DASHSCOPE_API_KEY 环境变量
