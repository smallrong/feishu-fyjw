package em.backend.dify.model.workflow;

import em.backend.dify.model.FileObject;
import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 工作流执行请求
 */
@Data
@Builder
public class WorkflowRunRequest {
    private Map<String, Object> inputs;
    private String response_mode;
    private String user;
    private List<FileObject> files;
    
    /**
     * 创建阻塞模式请求
     */
    public static WorkflowRunRequest createBlockingRequest(
            Map<String, Object> inputs,
            String user,
            List<FileObject> files) {
        return WorkflowRunRequest.builder()
                .inputs(inputs)
                .response_mode("blocking")
                .user(user)
                .files(files)
                .build();
    }
    
    /**
     * 创建流式模式请求
     */
    public static WorkflowRunRequest createStreamingRequest(
            Map<String, Object> inputs,
            String user,
            List<FileObject> files) {
        return WorkflowRunRequest.builder()
                .inputs(inputs)
                .response_mode("streaming")
                .user(user)
                .files(files)
                .build();
    }
} 