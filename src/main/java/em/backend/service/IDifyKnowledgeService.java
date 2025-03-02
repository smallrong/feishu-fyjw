package em.backend.service;

import lombok.Data;

/**
 * Dify知识库服务接口
 */
public interface IDifyKnowledgeService {
    
    @Data
    class KnowledgeResult {
        private String id;
        private String name;
        private String description;
        private String status;
        
        public KnowledgeResult(String id, String name, String description, String status) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.status = status;
        }
    }
    
    /**
     * 创建知识库
     * @param description 知识库描述
     * @return 知识库信息，包含id和状态，失败返回null
     */
    KnowledgeResult createKnowledge( String description);
    
    /**
     * 上传文件到知识库
     * @param knowledgeId 知识库ID
     * @param fileContent 文件内容
     * @param fileName 文件名
     * @return 是否上传成功
     */
    boolean uploadFile(String knowledgeId, byte[] fileContent, String fileName);
    
    /**
     * 获取知识库状态
     * @param knowledgeId 知识库ID
     * @return 知识库状态
     */
    String getKnowledgeStatus(String knowledgeId);
} 