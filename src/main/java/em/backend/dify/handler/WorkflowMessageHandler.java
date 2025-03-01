package em.backend.dify.handler;

import em.backend.dify.model.workflow.WorkflowChunkResponse;

/**
 * 工作流消息处理器接口
 */
public interface WorkflowMessageHandler {
    
    /**
     * 处理工作流流式响应块
     * 
     * @param chunkResponse 流式响应块
     */
    void handleMessage(WorkflowChunkResponse chunkResponse);
    
    /**
     * 工作流开始事件处理
     * 
     * @param chunkResponse 流式响应块
     */
    default void onWorkflowStarted(WorkflowChunkResponse chunkResponse) {
        // 默认实现为空，子类可以根据需要重写
    }
    
    /**
     * 节点开始事件处理
     * 
     * @param chunkResponse 流式响应块
     */
    default void onNodeStarted(WorkflowChunkResponse chunkResponse) {
        // 默认实现为空，子类可以根据需要重写
    }
    
    /**
     * 节点完成事件处理
     * 
     * @param chunkResponse 流式响应块
     */
    default void onNodeFinished(WorkflowChunkResponse chunkResponse) {
        // 默认实现为空，子类可以根据需要重写
    }
    
    /**
     * 工作流完成事件处理
     * 
     * @param chunkResponse 流式响应块
     */
    default void onWorkflowFinished(WorkflowChunkResponse chunkResponse) {
        // 默认实现为空，子类可以根据需要重写
    }
    
    /**
     * Ping事件处理
     * 
     * @param chunkResponse 流式响应块
     */
    default void onPing(WorkflowChunkResponse chunkResponse) {
        // 默认实现为空，子类可以根据需要重写
    }
} 