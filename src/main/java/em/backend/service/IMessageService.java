package em.backend.service;

import java.io.File;

/**
 * 消息服务接口
 */
public interface IMessageService {
    /**
     * 发送消息（带用户当前案件信息）
     * @param chatId 会话ID
     * @param content 消息内容
     * @param openId 用户ID，用于查询当前案件
     */
    void sendMessage(String chatId, String content, String openId);

    /**
     * 发送卡片消息
     * @param receiveId 接收者ID
     * @param cardContent 卡片内容
     */
    void sendCardMessage(String receiveId, String cardContent);

    /**
     * 发送流式消息
     * 
     * @param receiveId 接收者ID
     * @param initialContent 初始内容
     * @param title 卡片标题
     * @return 卡片实体ID和元素ID的组合，格式为"cardId:elementId"
     */
    String sendStreamingMessage(String receiveId, String initialContent, String title);

    /**
     * 更新流式消息内容
     * 
     * @param cardInfo 卡片信息，格式为"cardId:elementId"
     * @param content 更新的内容
     * @param sequence 序列号（需要递增）
     * @return 是否更新成功
     */
    boolean updateStreamingContent(String cardInfo, String content, int sequence);

    /**
     * 停止卡片的流式更新模式
     * 
     * @param cardInfo 卡片信息，格式为"cardId:elementId"
     * @param sequence 序列号
     * @return 是否成功
     */
    boolean stopStreamingMode(String cardInfo, int sequence);
    
    /**
     * 发送文件消息
     * 
     * @param receiveId 接收者ID
     * @param fileKey 文件Key
     * @param fileName 文件名称
     * @return 是否发送成功
     */
    boolean sendFileMessage(String receiveId, String fileKey, String fileName);

    /**
     * 回复消息
     * @param messageId 消息ID
     * @param content 消息内容
     * @param msgType 消息类型
     * @return 是否成功
     */
    boolean replyMessage(String messageId, String content, String msgType);
    
    /**
     * 更新卡片内容
     * @param cardId 卡片ID
     * @param content 新的卡片内容
     * @return 是否成功
     */
    boolean updateCardContent(String cardId, String content);
} 