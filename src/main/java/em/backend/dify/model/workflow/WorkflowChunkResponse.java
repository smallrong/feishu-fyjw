package em.backend.dify.model.workflow;

import lombok.Data;

/**
 * 工作流流式响应块
 */
@Data
public class WorkflowChunkResponse {
    private String event;
    private String task_id;
    private String workflow_run_id;
    private Object data;
    private Long created_at;
    
    /**
     * 判断是否为工作流开始事件
     */
    public boolean isWorkflowStarted() {
        return "workflow_started".equals(event);
    }
    
    /**
     * 判断是否为节点开始事件
     */
    public boolean isNodeStarted() {
        return "node_started".equals(event);
    }
    
    /**
     * 判断是否为节点完成事件
     */
    public boolean isNodeFinished() {
        return "node_finished".equals(event);
    }
    
    /**
     * 判断是否为工作流完成事件
     */
    public boolean isWorkflowFinished() {
        return "workflow_finished".equals(event);
    }
    
    /**
     * 判断是否为ping事件
     */
    public boolean isPing() {
        return "ping".equals(event);
    }
} 