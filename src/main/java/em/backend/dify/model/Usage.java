package em.backend.dify.model;

import lombok.Data;

/**
 * 模型用量信息
 */
@Data
public class Usage {
    private Integer prompt_tokens;
    private Integer completion_tokens;
    private Integer total_tokens;
} 