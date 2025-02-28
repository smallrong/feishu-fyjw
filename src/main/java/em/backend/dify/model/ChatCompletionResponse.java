package em.backend.dify.model;

import lombok.Data;
import java.util.List;

/**
 * 聊天完成响应
 */
@Data
public class ChatCompletionResponse {
    private String message_id;
    private String conversation_id;
    private String mode;
    private String answer;
    private Object metadata;
    private Usage usage;
    private List<RetrieverResource> retriever_resources;
    private long created_at;
} 