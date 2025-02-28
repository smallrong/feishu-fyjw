package em.backend.dify.handler;

import com.alibaba.fastjson2.JSONObject;
import em.backend.dify.MessageHandler;
import em.backend.dify.model.ChunkResponse;
import em.backend.service.IMessageService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * 飞书流式消息处理器
 * 用于处理Dify流式响应并更新飞书卡片
 */
@Slf4j
public class FeishuStreamingMessageHandler implements MessageHandler, Consumer<ChunkResponse> {
    private final String cardInfo;
    private final IMessageService messageService;
    private int sequence = 2;
    private StringBuilder fullContent = new StringBuilder();
    boolean isflag = true; // 来判断当前响应是否正常，如果为false相当于是10次重试都用完了

    public FeishuStreamingMessageHandler(String cardInfo, IMessageService messageService) {
        this.cardInfo = cardInfo;
        this.messageService = messageService;
    }
    
    @Override
    public synchronized void accept(ChunkResponse chunkResponse) {
        try {
            // 处理ChunkResponse对象
            if (chunkResponse == null || chunkResponse.getEvent() == null) {
                return;
            }
            
            String event = chunkResponse.getEvent();
            switch (event) {
                case "message":
                    handleMessageChunk(chunkResponse);
                    break;
                case "error":
                    handleErrorChunk(chunkResponse);
                    break;
                case "message_end":
                    log.info("流式响应结束，停止卡片流式更新");
                    // 停止卡片流式更新，使用当前序列号
                    messageService.stopStreamingMode(cardInfo, sequence);
                    break;
                default:
                    // 忽略其他事件类型
                    break;
            }
        } catch (Exception e) {
            log.error("处理响应异常: {}", e.getMessage());
        }
    }
    
    private void handleMessageChunk(ChunkResponse chunkResponse) {
        String answer = chunkResponse.getAnswer();
        if (answer == null || answer.isEmpty()) {
            return;
        }
        
        fullContent.append(answer);
        int MAX_C = 0;
        if(isflag)MAX_C =10;
        while(MAX_C > 0){
            boolean success = messageService.updateStreamingContent(cardInfo, fullContent.toString(), sequence);
            if (success) {
                sequence++; // 只有在成功更新后才递增序列号
                break;
            }
            MAX_C--;
        }
        if(MAX_C == 0){
            log.error("更新流式消息失败 达到最大次数");
            messageService.stopStreamingMode(cardInfo, sequence);
            isflag = false;
        }
    }
    
    private void handleErrorChunk(ChunkResponse chunkResponse) {
        String errorMsg = chunkResponse.getMessage();
        log.error("Dify返回错误: {}", errorMsg);
        messageService.updateStreamingContent(cardInfo, 
            fullContent + "\n\n❌ 错误: " + errorMsg, sequence++);
        
        // 错误后也需要停止流式更新
        messageService.stopStreamingMode(cardInfo, sequence);
    }
    
    @Override
    public void onMessage(String message) {
        try {
            if (message == null || message.isEmpty()) {
                return;
            }
            
            JSONObject jsonMessage = JSONObject.parseObject(message);
            String event = jsonMessage.getString("event");
            
            if (event == null) {
                return;
            }
            
            switch (event) {
                case "message":
                    handleMessageEvent(jsonMessage);
                    break;
                case "error":
                    handleErrorEvent(jsonMessage);
                    break;
                case "message_end":
                    // 消息结束，停止流式更新
                    log.info("流式响应结束，停止卡片流式更新");
                    messageService.stopStreamingMode(cardInfo, sequence);
                    break;
                default:
                    // 忽略其他事件类型
                    break;
            }
        } catch (Exception e) {
            log.error("处理流式消息失败", e);
        }
    }
    
    private void handleMessageEvent(JSONObject jsonMessage) {
        String answer = jsonMessage.getString("answer");
        if (answer != null && !answer.isEmpty()) {
            fullContent.append(answer);
            messageService.updateStreamingContent(cardInfo, fullContent.toString(), sequence++);
        }
    }
    
    private void handleErrorEvent(JSONObject jsonMessage) {
        String errorMsg = jsonMessage.getString("message");
        messageService.updateStreamingContent(cardInfo, 
            fullContent + "\n\n❌ 错误: " + errorMsg, sequence++);
        
        // 错误后也需要停止流式更新
        messageService.stopStreamingMode(cardInfo, sequence);
    }
    
    @Override
    public void onComplete() {
        // 流式响应完成，不需要特殊处理
        log.info("流式响应完成");
    }
    
    @Override
    public void onError(Throwable throwable) {
        try {
            log.error("流式响应出错: {}", throwable.getMessage());
            messageService.updateStreamingContent(cardInfo, 
                fullContent + "\n\n❌ 系统错误: " + throwable.getMessage(), sequence++);
            
            // 错误后也需要停止流式更新
            messageService.stopStreamingMode(cardInfo, sequence);
        } catch (Exception e) {
            log.error("更新错误消息失败: {}", e.getMessage());
        }
    }
}