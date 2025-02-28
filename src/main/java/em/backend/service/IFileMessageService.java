package em.backend.service;

/**
 * 文件消息处理服务接口
 */
public interface IFileMessageService {
    
    /**
     * 处理文件消息
     * @param userId 用户ID
     * @param chatId 会话ID
     * @param messageId 消息ID
     * @param fileKey 文件key
     * @param fileName 文件名
     * @return 处理结果
     */
    boolean handleFileMessage(String userId, String chatId, String messageId, String fileKey, String fileName);
}