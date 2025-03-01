package em.backend.dify.model.workflow;

import lombok.Data;

/**
 * 工作流节点数据
 */
@Data
public class WorkflowNodeData {
    
    /**
     * 节点开始数据
     */
    @Data
    public static class NodeStartData {
        private String id;
        private String node_id;
        private String node_type;
        private String title;
        private Integer index;
        private String predecessor_node_id;
        private Object inputs;
        private Long created_at;
    }
    
    /**
     * 节点完成数据
     */
    @Data
    public static class NodeFinishData {
        private String id;
        private String node_id;
        private Integer index;
        private String predecessor_node_id;
        private Object inputs;
        private Object process_data;
        private Object outputs;
        private String status;
        private String error;
        private Float elapsed_time;
        private ExecutionMetadata execution_metadata;
        private Long created_at;
        
        @Data
        public static class ExecutionMetadata {
            private Integer total_tokens;
            private Double total_price;
            private String currency;
        }
    }
    
    /**
     * 工作流开始数据
     */
    @Data
    public static class WorkflowStartData {
        private String id;
        private String workflow_id;
        private Integer sequence_number;
        private Long created_at;
    }
    
    /**
     * 工作流完成数据
     */
    @Data
    public static class WorkflowFinishData {
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