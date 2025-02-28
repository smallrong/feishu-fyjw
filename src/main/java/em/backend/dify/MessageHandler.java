package em.backend.dify;

/**
 * 消息处理器接口
 * 用于处理流式响应
 */
public interface MessageHandler {
    
    /**
     * 处理消息
     * @param message 消息内容
     */
    void onMessage(String message);
    
    /**
     * 处理完成
     */
    void onComplete();
    
    /**
     * 处理错误
     * @param throwable 异常
     */
    void onError(Throwable throwable);
} 