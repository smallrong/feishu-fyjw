package em.backend.controller;

import em.backend.dify.IDifyClient;
import em.backend.dify.model.workflow.WorkflowCompletionResponse;
import em.backend.dify.model.workflow.WorkflowChunkResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作流控制器
 */
@RestController
@RequestMapping("/api/workflow")
@Slf4j
public class WorkflowController {

    @Autowired
    private IDifyClient difyClient;
    
    // 存储SSE连接，用于流式响应
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    /**
     * 执行工作流（阻塞模式）
     * 
     * @param inputs 变量值
     * @param userId 用户ID
     * @param apiKey API Key
     * @return 工作流执行响应
     */
    @PostMapping("/run")
    public WorkflowCompletionResponse runWorkflow(
            @RequestBody Map<String, Object> inputs,
            @RequestParam String userId,
            @RequestParam String apiKey) {
        
        log.info("【工作流控制器】开始执行工作流（阻塞模式）: userId={}, inputs={}", userId, inputs);
        
        try {
            // 调用Dify客户端执行工作流
            WorkflowCompletionResponse response = difyClient.runWorkflowBlocking(inputs, userId, null, apiKey);
            
            log.info("【工作流控制器】工作流执行完成（阻塞模式）: workflowRunId={}, taskId={}, status={}", 
                    response.getWorkflow_run_id(), response.getTask_id(), 
                    response.getData() != null ? response.getData().getStatus() : "unknown");
            return response;
        } catch (Exception e) {
            log.error("【工作流控制器】工作流执行异常（阻塞模式）", e);
            throw e;
        }
    }
    
    /**
     * 执行工作流（流式模式）
     * 
     * @param inputs 变量值
     * @param userId 用户ID
     * @param apiKey API Key
     * @return SSE发射器
     */
    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runWorkflowStream(
            @RequestBody Map<String, Object> inputs,
            @RequestParam String userId,
            @RequestParam String apiKey) {
        
        log.info("【工作流控制器】开始执行工作流（流式模式）: userId={}, inputs={}", userId, inputs);
        
        // 创建唯一的连接ID
        String connectionId = UUID.randomUUID().toString();
        log.debug("【工作流控制器】创建SSE连接: connectionId={}", connectionId);
        
        // 创建SSE发射器，设置超时时间为30分钟
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        // 存储SSE发射器
        sseEmitters.put(connectionId, emitter);
        
        // 设置完成回调，移除SSE发射器
        emitter.onCompletion(() -> {
            log.info("【工作流控制器】SSE连接完成: connectionId={}", connectionId);
            sseEmitters.remove(connectionId);
        });
        
        // 设置超时回调
        emitter.onTimeout(() -> {
            log.info("【工作流控制器】SSE连接超时: connectionId={}", connectionId);
            sseEmitters.remove(connectionId);
        });
        
        // 设置错误回调
        emitter.onError(e -> {
            log.error("【工作流控制器】SSE连接错误: connectionId={}", connectionId, e);
            sseEmitters.remove(connectionId);
        });
        
        // 创建工作流消息处理器
        Consumer<WorkflowChunkResponse> messageHandler = chunkResponse -> {
            try {
                log.debug("【工作流控制器】收到工作流事件: event={}, taskId={}", 
                        chunkResponse.getEvent(), chunkResponse.getTask_id());
                
                // 发送SSE事件
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(chunkResponse));
                
                // 如果是工作流完成事件，关闭SSE连接
                if (chunkResponse.isWorkflowFinished()) {
                    log.info("【工作流控制器】工作流执行完成（流式模式）: workflowRunId={}, taskId={}", 
                            chunkResponse.getWorkflow_run_id(), chunkResponse.getTask_id());
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("【工作流控制器】发送SSE事件失败", e);
                emitter.completeWithError(e);
            }
        };
        
        try {
            // 调用Dify客户端执行工作流（流式模式）
            log.debug("【工作流控制器】开始调用Dify客户端执行工作流（流式模式）");
            difyClient.runWorkflowStreaming(inputs, userId, messageHandler, null, apiKey);
        } catch (Exception e) {
            log.error("【工作流控制器】工作流执行异常（流式模式）", e);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
    
    /**
     * 停止工作流执行
     * 
     * @param taskId 任务ID
     * @param apiKey API Key
     * @return 是否成功停止
     */
    @PostMapping("/stop")
    public Map<String, Boolean> stopWorkflow(
            @RequestParam String taskId,
            @RequestParam String apiKey) {
        
        log.info("【工作流控制器】停止工作流执行: taskId={}", taskId);
        
        boolean result = false;
        try {
            result = difyClient.stopWorkflow(taskId, apiKey);
            log.info("【工作流控制器】工作流停止结果: taskId={}, result={}", taskId, result);
        } catch (Exception e) {
            log.error("【工作流控制器】停止工作流异常: taskId={}", taskId, e);
        }
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", result);
        
        return response;
    }
}