# A21-agentscope-classify

基于 AgentScope Java **HarnessAgent** 的水族灯意图识别 + 多 Agent 编排示例。

## 模块要点

- **OrchestratorAgent + 5 个子 Agent**：编排层只挂 `classify_intent` 工具，
  真正派发由纯 Java 的 `IntentRouter` 完成。
- **意图分类**：规则引擎 + DashScope 小模型（`IntentClassifier`），10ms 内可命中。
- **共享工具层**：`sync_device_state` / `validate_params` / `send_control_cmd` /
  `query_device_status` / `query_device_schedule` / `query_device_history` /
  `aquarium_knowledge` / `device_manual`，由各 Agent 按需装配。
- **Plan Mode**：模糊控制场景复杂度评分达到阈值时，`FuzzyControlAgent` 会进入
  Plan Mode 生成方案等待 HITL 确认。

## 目录结构

```
src/main/java/org/happyhai/agentscope/classify
├── A21AgentscopeClassifyApplication.java   # Spring Boot 入口
├── agent/
│   └── SubAgentConfigs.java                # 5 个子 Agent + Orchestrator Agent bean
├── controller/
│   ├── OrchestratorStreamController.java   # GET  /orchestrator/chat/stream（SSE）
│   └── ClassifyController.java             # /intelligence/classify /intelligence/chat
├── domain/                                  # DeviceState / ChannelState / ValidationResult
├── intent/
│   ├── IntentType.java                     # 五种意图枚举
│   ├── IntentResult.java                   # 分类结果 record（含 complexity_score）
│   ├── IntentClassifier.java               # 规则 + LLM 混合分类器（也是 Tool）
│   └── IntentRouter.java                   # 确定性 Java 路由（switch）
├── orchestrator/
│   └── AquariumLightOrchestrator.java      # 主调度 Service
├── service/
│   └── InMemoryDeviceService.java          # 内存版设备后端（mock）
└── tool/                                    # @Tool 标注的共享工具
```

## HTTP 入口

| 方法   | 路径                          | 说明                                      |
| ------ | ----------------------------- | ----------------------------------------- |
| GET    | `/orchestrator/health`        | 健康检查 + Agent 列表                    |
| GET    | `/orchestrator/chat/stream`   | SSE 流式：先推意图帧，再委派子 Agent     |
| GET    | `/intelligence/classify`      | 仅跑意图分类，不委派                    |
| POST   | `/intelligence/chat`          | 同步跑分类 + 路由 + 委派                 |

### 示例

```bash
# 纯意图分类
curl 'http://localhost:20021/intelligence/classify?prompt=把主灯亮度调到60%25'

# 同步全流程
curl -X POST 'http://localhost:20021/intelligence/chat' \
     -d 'userId=u1&sessionId=s1&prompt=帮我弄一个适合草缸的灯光方案'

# 流式（SSE）
curl 'http://localhost:20021/orchestrator/chat/stream?userId=u1&sessionId=s1&prompt=现在灯在什么模式？'
```

## 配置

```yaml
agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-plus

classify:
  rule-classifier-enabled: true
  fuzzy:
    plan-mode-threshold: 2
    max-iterations: 30
```

## 设计稿对应关系

| 设计稿章节            | 本模块实现                                  |
| --------------------- | ------------------------------------------- |
| 2.1 五种意图定义      | `IntentType`                                |
| 2.2 明确 vs 模糊判定  | `IntentClassifier` 规则集                   |
| 2.3 Slot 抽取结构     | `IntentResult.slots()`                      |
| 2.4 意图分类工具      | `IntentClassifier.classifyIntent()`（@Tool）|
| 3.1 闲聊 CHIT_CHAT    | `chatAgent`                                 |
| 3.2 知识问答          | `knowledgeAgent`                            |
| 3.3 明确控制          | `explicitControlAgent`                      |
| 3.4 模糊控制 + Plan Mode | `fuzzyControlAgent` + `enablePlanMode()`   |
| 3.5 状态查询          | `statusQueryAgent`                          |
| 4.1 设备状态同步      | `SyncDeviceStateTool`                       |
| 4.2 参数校验与兜底    | `ValidateParamsTool`                        |
| 5 编排层 Intent Router | `IntentRouter` + `AquariumLightOrchestrator` |
