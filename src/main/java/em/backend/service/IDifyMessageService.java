package em.backend.service;

/**
 * Dify消息服务接口
 * 负责处理用户消息并通过流式卡片返回结果
 */
public interface IDifyMessageService {
    
    /**
     * 处理用户消息并通过流式卡片返回结果
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @param query 用户输入内容
     * @return 处理结果
     */
    boolean handleUserMessage(String userId, String chatId, String query,String conversationId,String _apikey);
} 