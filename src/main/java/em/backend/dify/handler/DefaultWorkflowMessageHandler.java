package em.backend.dify.handler;

import em.backend.dify.model.workflow.WorkflowChunkResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流消息处理器默认实现
 */
@Slf4j
public class DefaultWorkflowMessageHandler implements WorkflowMessageHandler {
    
    @Override
    public void handleMessage(WorkflowChunkResponse chunkResponse) {
        log.debug("【工作流消息处理器】收到工作流事件: event={}, taskId={}, workflowRunId={}", 
                chunkResponse.getEvent(), chunkResponse.getTask_id(), chunkResponse.getWorkflow_run_id());
        
        if (chunkResponse.isWorkflowStarted()) {
            onWorkflowStarted(chunkResponse);
        } else if (chunkResponse.isNodeStarted()) {
            onNodeStarted(chunkResponse);
        } else if (chunkResponse.isNodeFinished()) {
            onNodeFinished(chunkResponse);
        } else if (chunkResponse.isWorkflowFinished()) {
            onWorkflowFinished(chunkResponse);
        } else if (chunkResponse.isPing()) {
            onPing(chunkResponse);
        } else {
            log.warn("【工作流消息处理器】未知的工作流事件类型: {}", chunkResponse.getEvent());
        }
    }
    
    @Override
    public void onWorkflowStarted(WorkflowChunkResponse chunkResponse) {
        log.info("【工作流消息处理器】工作流开始执行: workflowRunId={}, taskId={}, data={}", 
                chunkResponse.getWorkflow_run_id(), chunkResponse.getTask_id(), chunkResponse.getData());
    }
    
    @Override
    public void onNodeStarted(WorkflowChunkResponse chunkResponse) {
        log.info("【工作流消息处理器】节点开始执行: workflowRunId={}, taskId={}, data={}", 
                chunkResponse.getWorkflow_run_id(), chunkResponse.getTask_id(), chunkResponse.getData());
    }
    
    @Override
    public void onNodeFinished(WorkflowChunkResponse chunkResponse) {
        log.info("【工作流消息处理器】节点执行完成: workflowRunId={}, taskId={}, data={}", 
                chunkResponse.getWorkflow_run_id(), chunkResponse.getTask_id(), chunkResponse.getData());
    }
    
    @Override
    public void onWorkflowFinished(WorkflowChunkResponse chunkResponse) {
        log.info("【工作流消息处理器】工作流执行完成: workflowRunId={}, taskId={}, data={}", 
                chunkResponse.getWorkflow_run_id(), chunkResponse.getTask_id(), chunkResponse.getData());
    }
    
    @Override
    public void onPing(WorkflowChunkResponse chunkResponse) {
        log.debug("【工作流消息处理器】收到Ping事件: taskId={}", chunkResponse.getTask_id());
    }
} 