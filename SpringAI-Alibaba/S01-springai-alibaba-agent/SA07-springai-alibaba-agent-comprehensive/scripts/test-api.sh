#水族灯控制 MCP Agent API 调用示例

#启动前设置环境变量
#export MCP_SERVER_URL=http://localhost:8080
#export DASHSCOPE_API_KEY=your_api_key

#服务地址
BASE_URL=http://localhost:10007/api/aquarium-light

#============================================
#1. 查询设备列表
#============================================
echo === 1. 查询设备列表 ===
curl -s "/devices?userId=user001" | jq .

#============================================
#2. 获取支持的命令码
#============================================
echo.
echo === 2. 获取支持的命令码 ===
curl -s "/commands" | jq .

#============================================
#3. 对话：开灯（首次对话，Agent 调用 getOnlineDevicesByUniqueId）
#============================================
echo.
echo === 3. 对话：开灯 ===
curl -s "/chat?message=开灯&userId=user001" | jq .

#============================================
#4. 选择设备后继续对话（Agent 调用 publishDeviceCommands，触发 HITL 中断）
#假设上一步返回 traceId=test-trace-123，选择 device001
#============================================
echo.
echo === 4. 选择设备继续对话 ===
curl -s "/chat?message=开灯&userId=user001&traceId=test-trace-123&selectedDeviceId=device001" | jq .

#============================================
#5. 批准灯光控制操作
#============================================
echo.
echo === 5. 批准灯光控制 ===
curl -s -X POST "/confirm?traceId=test-trace-123&approved=true" | jq .

#============================================
#6. 查询操作状态
#============================================
echo.
echo === 6. 查询状态 ===
curl -s "/status/test-trace-123" | jq .

#============================================
#7. 拒绝灯光控制
#============================================
echo.
echo === 7. 拒绝灯光控制 ===
curl -s -X POST "/confirm?traceId=test-trace-123&approved=false&feedback=不想开灯" | jq .
