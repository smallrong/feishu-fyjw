package em.backend.dify.model;

import lombok.Data;
import java.util.List;

/**
 * 流式响应块
 */
@Data
public class ChunkResponse {
    private String event;
    private String task_id;
    private String message_id;
    private String conversation_id;
    private String answer;
    private long created_at;
    
    // agent_thought 事件特有字段
    private String id;
    private Integer position;
    private String thought;
    private String observation;
    private String tool;
    private String tool_input;
    private List<String> message_files;
    
    // message_file 事件特有字段
    private String type;
    private String belongs_to;
    private String url;
    
    // message_end 事件特有字段
    private Object metadata;
    private Usage usage;
    private List<RetrieverResource> retriever_resources;
    
    // tts_message 事件特有字段
    private String audio;
    
    // error 事件特有字段
    private Integer status;
    private String code;
    private String message;
} 