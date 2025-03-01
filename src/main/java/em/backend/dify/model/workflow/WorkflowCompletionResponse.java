package em.backend.dify.model.workflow;

import lombok.Data;

/**
 * 工作流执行响应（阻塞模式）
 */
@Data
public class WorkflowCompletionResponse {
    private String workflow_run_id;
    private String task_id;
    private WorkflowData data;
    
    @Data
    public static class WorkflowData {
        private String id;
        private String workflow_id;
        private String status;
        private Object outputs;
        private String error;
        private Float elapsed_time;
        private Integer total_tokens;
        private Integer total_steps;
        private Long created_at;
        private Long finished_at;
    }
} 