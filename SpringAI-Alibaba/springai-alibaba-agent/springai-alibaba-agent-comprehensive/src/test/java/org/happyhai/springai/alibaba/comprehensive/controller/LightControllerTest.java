package org.happyhai.springai.alibaba.comprehensive.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.happyhai.springai.alibaba.comprehensive.domain.DeviceInfo;
import org.happyhai.springai.alibaba.comprehensive.domain.TraceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 水族灯控制 Controller 测试
 * 测试 MCP 工具集成后的 API 接口
 */
@WebMvcTest(LightController.class)
class LightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TraceInfoService traceInfoService;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private ReactAgent reactAgent;

    private static final String BASE_URL = "/api/aquarium-light";

    @BeforeEach
    void setUp() {
        // Mock TraceInfoService
        when(traceInfoService.generateTraceId()).thenReturn("test-trace-123");
        when(traceInfoService.saveTraceInfo(any(TraceInfo.class))).thenReturn("test-trace-123");
        when(traceInfoService.getTraceInfo(anyString())).thenReturn(Optional.empty());
    }

    /**
     * 测试1: 查询设备列表
     * GET /api/aquarium-light/devices
     */
    @Test
    void testGetDevices() throws Exception {
        List<DeviceInfo> devices = List.of(
                new DeviceInfo("device001", "0x0102A201", "水族灯 Pro", true, "user001"),
                new DeviceInfo("device002", "0x0102A202", "水族灯 Mini", true, "user001")
        );
        when(deviceService.getOnlineDevicesByUserId("user001")).thenReturn(devices);
        when(deviceService.getDevicesByUserId("user001")).thenReturn(devices);

        mockMvc.perform(get(BASE_URL + "/devices")
                        .param("userId", "user001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.onlineDevices").isArray())
                .andExpect(jsonPath("$.onlineDevices.length()").value(2))
                .andExpect(jsonPath("$.onlineDevices[0].deviceId").value("device001"))
                .andExpect(jsonPath("$.onlineDevices[0].productCode").value("0x0102A201"));
    }

    /**
     * 测试2: 新对话 - Agent 调用 getOnlineDevicesByUniqueId 工具
     * GET /api/aquarium-light/chat
     *
     * 测试场景：用户发送"开灯"，首次对话会先用 getOnlineDevicesByUniqueId 查询设备
     */
    @Test
    void testChat_FirstRequest_CallsGetOnlineDevicesTool() throws Exception {
        List<DeviceInfo> devices = List.of(
                new DeviceInfo("device001", "0x0102A201", "水族灯 Pro", true, "user001")
        );
        when(deviceService.getOnlineDevicesByUserId("user001")).thenReturn(devices);
        when(deviceService.getDevicesByUserId("user001")).thenReturn(devices);

        // Agent 返回设备列表（包含"找到"和"设备"关键字）
        when(reactAgent.invokeAndGetOutput(anyString(), any()))
                .thenReturn(Optional.of(new NodeOutput() {
                    @Override
                    public String toString() {
                        return "找到 1 个设备：\n1. 设备ID: device001, 名称: 水族灯 Pro, 产品码: 0x0102A201, 状态: 在线";
                    }
                }));

        mockMvc.perform(get(BASE_URL + "/chat")
                        .param("message", "开灯")
                        .param("userId", "user001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("test-trace-123"))
                .andExpect(jsonPath("$.status").value("NEED_DEVICE_SELECTION"))
                .andExpect(jsonPath("$.needDeviceSelection").value(true))
                .andExpect(jsonPath("$.devices").isArray());
    }

    /**
     * 测试3: 选择设备后对话 - Agent 调用 publishDeviceCommands 工具，触发 HITL 中断
     * GET /api/aquarium-light/chat
     */
    @Test
    void testChat_DeviceSelected_CallsPublishDeviceCommandsTool() throws Exception {
        DeviceInfo device = new DeviceInfo("device001", "0x0102A201", "水族灯 Pro", true, "user001");
        when(deviceService.getDeviceById("device001")).thenReturn(Optional.of(device));

        // Agent 调用 publishDeviceCommands，触发 HITL 中断
        InterruptionMetadata interruption = InterruptionMetadata.builder()
                .nodeId("thread-test-trace-123")
                .state(null)
                .build();
        interruption.addToolFeedback(InterruptionMetadata.ToolFeedback.builder()
                .name("publishDeviceCommands")
                .description("设备控制操作需要人工审批确认")
                .result(InterruptionMetadata.ToolFeedback.FeedbackResult.PENDING)
                .build());

        when(reactAgent.invokeAndGetOutput(anyString(), any()))
                .thenReturn(Optional.of(interruption));

        mockMvc.perform(get(BASE_URL + "/chat")
                        .param("message", "开灯")
                        .param("userId", "user001")
                        .param("traceId", "test-trace-123")
                        .param("selectedDeviceId", "device001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.interrupted").value(true))
                .andExpect(jsonPath("$.toolFeedbacks").isArray())
                .andExpect(jsonPath("$.toolFeedbacks[0].tool").value("publishDeviceCommands"));
    }

    /**
     * 测试4: 确认灯光控制操作
     * POST /api/aquarium-light/confirm
     */
    @Test
    void testConfirm_Approved() throws Exception {
        TraceInfo traceInfo = new TraceInfo("test-trace-123", "user001", "PENDING_CONFIRMATION", "thread-test-trace-123");
        traceInfo.setDeviceId("device001");
        traceInfo.setDeviceName("水族灯 Pro");
        traceInfo.setAction("开");
        traceInfo.setCommand("0x0102A201");

        when(traceInfoService.getTraceInfo("test-trace-123")).thenReturn(Optional.of(traceInfo));
        when(deviceService.getLightCommand(anyString())).thenReturn(Optional.empty());
        when(traceInfoService.updateStatus(anyString(), any())).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/confirm")
                        .param("traceId", "test-trace-123")
                        .param("approved", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    /**
     * 测试5: 拒绝灯光控制操作
     * POST /api/aquarium-light/confirm
     */
    @Test
    void testConfirm_Rejected() throws Exception {
        TraceInfo traceInfo = new TraceInfo("test-trace-123", "user001", "PENDING_CONFIRMATION", "thread-test-trace-123");
        traceInfo.setDeviceId("device001");
        traceInfo.setDeviceName("水族灯 Pro");
        traceInfo.setAction("开");
        traceInfo.setCommand("0x0102A201");

        when(traceInfoService.getTraceInfo("test-trace-123")).thenReturn(Optional.of(traceInfo));
        when(traceInfoService.updateStatus(anyString(), any())).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/confirm")
                        .param("traceId", "test-trace-123")
                        .param("approved", "false")
                        .param("feedback", "不想开灯"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.feedback").value("不想开灯"));
    }

    /**
     * 测试6: 查询状态
     * GET /api/aquarium-light/status/{traceId}
     */
    @Test
    void testGetStatus() throws Exception {
        TraceInfo traceInfo = new TraceInfo("test-trace-123", "user001", "PENDING_CONFIRMATION", "thread-test-trace-123");
        traceInfo.setDeviceId("device001");
        traceInfo.setDeviceName("水族灯 Pro");
        traceInfo.setAction("开");

        when(traceInfoService.getTraceInfo("test-trace-123")).thenReturn(Optional.of(traceInfo));

        mockMvc.perform(get(BASE_URL + "/status/test-trace-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("test-trace-123"))
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.deviceId").value("device001"))
                .andExpect(jsonPath("$.deviceName").value("水族灯 Pro"))
                .andExpect(jsonPath("$.action").value("开"));
    }
}
