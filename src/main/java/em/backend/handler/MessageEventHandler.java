package em.backend.handler;

import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import em.backend.service.IDifyMessageService;
import em.backend.service.IFileMessageService;
import em.backend.service.IUserStatusService;
import em.backend.pojo.UserStatus;
import em.backend.service.impl.CaseServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventHandler implements IEventHandler<P2MessageReceiveV1, Void> {
    
    private final IDifyMessageService difyMessageService;
    private final IFileMessageService fileMessageService;
    private final IUserStatusService userStatusService;
    
    @Override
    public Void handle(P2MessageReceiveV1 event) {
        try {
            String messageType = event.getEvent().getMessage().getMessageType();
            String chatId = event.getEvent().getMessage().getChatId();
            String userId = event.getEvent().getSender().getSenderId().getOpenId();
            String messageId = event.getEvent().getMessage().getMessageId();
            
            log.info("收到消息事件: event=[{}]", JSONObject.toJSONString(event));
            log.info("收到消息: type=[{}], user=[{}]", messageType, userId);


            switch (messageType) {
                case "text":
                    // 处理文本消息
                    String text = JSONObject.parseObject(event.getEvent().getMessage().getContent())
                            .getString("text");
                    String _apikey = null;
                    // 获取用户状态，检查是否在法律研究模式
                    UserStatus userStatus = userStatusService.lambdaQuery()
                            .eq(UserStatus::getOpenId, userId)
                            .one();
                    String groupId = null;
                    if (userStatus != null && userStatus.isLegal()) {
                        System.out.println("用户状态 +"+ userStatus.getCurrentLegalResearchGroupId());
                        _apikey = CaseServiceImpl.LEGAL_RESEARCH_CHAT_KEY;
                        if(userStatus.getCurrentLegalResearchGroupId() != null) {
                            groupId = userStatus.getCurrentLegalResearchGroupId();
                        }
                        log.info("用户处于法律研究模式，对话组ID: {}", groupId);
                    }
                    
                    difyMessageService.handleUserMessage(userId, chatId, text, groupId, _apikey);
                    break;
                    
                case "file":
                    // 打印文件消息的具体内容
                    String content = event.getEvent().getMessage().getContent();
                    log.info("文件消息内容: content=[{}]", content);
                    JSONObject fileInfo = JSONObject.parseObject(content);
                    String fileKey = fileInfo.getString("file_key");
                    String fileName = fileInfo.getString("file_name");
                    fileMessageService.handleFileMessage(userId, chatId, messageId, fileKey, fileName);
                    break;
                    
                default:
                    log.info("未处理的消息类型: {}", messageType);
                    break;
            }
        } catch (Exception e) {
            log.error("处理消息事件异常", e);
        }
        return null;
    }
}