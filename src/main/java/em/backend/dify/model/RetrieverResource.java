package em.backend.dify.model;

import lombok.Data;

/**
 * 引用和归属分段
 */
@Data
public class RetrieverResource {
    private String id;
    private String segment;
    private Double score;
    private String document_id;
    private String document_name;
    private String document_type;
} 