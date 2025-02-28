package em.backend.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.service.cardkit.v1.model.*;
import em.backend.service.IMessageService;
import em.backend.service.ICardTemplateService;
import em.backend.service.IUserStatusService;
import em.backend.pojo.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements IMessageService {
    
    private final Client feishuClient;
    private final ICardTemplateService cardTemplateService;
    private final IUserStatusService userStatusService;

    @Override
    public void sendMessage(String chatId, String content, String openId) {
        try {
            // 查询用户当前案件
            UserStatus userStatus = userStatusService.lambdaQuery()
                .eq(UserStatus::getOpenId, openId)
                .one();
            
            // 构建消息卡片
            String currentCase = userStatus != null && userStatus.getCurrentCaseName() != null 
                ? userStatus.getCurrentCaseName() 
                : "未选择案件";
            
            String cardContent = cardTemplateService.buildMessageCard(content, currentCase);

            // 发送卡片消息
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(ReceiveIdTypeEnum.CHAT_ID.getValue())
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(chatId)
                            .msgType(MsgTypeEnum.MSG_TYPE_INTERACTIVE.getValue())
                            .content(cardContent)
                            .build())
                    .build();

            CreateMessageResp resp = feishuClient.im().message().create(req);
            if (resp.getCode() != 0) {
                log.error("发送消息卡片失败, code: {}, msg: {}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception e) {
            log.error("发送消息卡片错误", e);
        }
    }

    @Override
    public void sendCardMessage(String receiveId, String cardContent) {
        try {
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id")  // 明确指定接收者ID类型
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId)
                            .msgType(MsgTypeEnum.MSG_TYPE_INTERACTIVE.getValue())
                            .content(cardContent)
                            .build())
                    .build();

            CreateMessageResp resp = feishuClient.im().message().create(req);
            if (resp.getCode() != 0) {
                log.error("发送卡片消息失败, code: {}, msg: {}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception e) {
            log.error("发送卡片信息错误", e);
        }
    }

    @Override
    public String sendStreamingMessage(String receiveId, String initialContent, String title) {
        try {
            // 1. 使用卡片模板服务构建流式卡片
            String jsonTemplate = cardTemplateService.buildStreamingCard(title);
            if (jsonTemplate == null) {
                return null;
            }
            
            // 2. 创建卡片实体
            CreateCardReq req = CreateCardReq.newBuilder()
                .createCardReqBody(CreateCardReqBody.newBuilder()
                    .type("card_json")
                    .data(jsonTemplate)
                    .build())
                .build();
            
            CreateCardResp resp = feishuClient.cardkit().v1().card().create(req);
            
            if (!resp.success()) {
                log.error("创建卡片实体失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return null;
            }
            
            String cardId = resp.getData().getCardId();
            
            // 3. 使用卡片模板服务构建卡片实体内容
            String cardContent = cardTemplateService.buildCardEntityContent(cardId);
            
            // 4. 发送卡片实体
            CreateMessageReq sendReq = CreateMessageReq.newBuilder()
                .receiveIdType("open_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(receiveId)
                    .msgType("interactive")
                    .content(cardContent)
                    .build())
                .build();
            
            CreateMessageResp sendResp = feishuClient.im().v1().message().create(sendReq);
            
            if (sendResp.getCode() != 0) {
                log.error("发送卡片实体失败: code={}, msg={}", sendResp.getCode(), sendResp.getMsg());
                return null;
            }
            
            // 5. 更新初始内容
            if (initialContent != null && !initialContent.isEmpty()) {
                updateStreamingContent(cardId + ":streaming_sey", initialContent, 1);
            }
            
            return cardId + ":streaming_sey";
        } catch (Exception e) {
            log.error("发送流式消息失败", e);
            return null;
        }
    }
    
    @Override
    public boolean updateStreamingContent(String cardInfo, String content, int sequence) {
        try {
            String[] parts = cardInfo.split(":");
            if (parts.length != 2) {
                log.error("卡片信息格式错误: {}", cardInfo);
                return false;
            }
            
            String cardId = parts[0];
            String elementId = parts[1];
            
            ContentCardElementReq req = ContentCardElementReq.newBuilder()
                .cardId(cardId)
                .elementId(elementId)
                .contentCardElementReqBody(ContentCardElementReqBody.newBuilder()
                    .content(content)
                    .sequence(sequence)
                    .build())
                .build();
            
            ContentCardElementResp resp = feishuClient.cardkit().v1().cardElement().content(req);
            
            if (!resp.success()) {
                log.error("更新流式消息内容失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("更新流式消息内容失败", e);
            return false;
        }
    }

    @Override
    public boolean stopStreamingMode(String cardInfo, int sequence) {
        try {
            String[] parts = cardInfo.split(":");
            if (parts.length != 2) {
                log.error("卡片信息格式错误: {}", cardInfo);
                return false;
            }
            
            String cardId = parts[0];
            
            log.info("停止卡片流式更新: cardId=[{}], sequence=[{}]", cardId, sequence);
            
            // 构建请求体
            JSONObject settings = new JSONObject();
            JSONObject config = new JSONObject();
            config.put("streaming_mode", false);
            settings.put("config", config);
            
            // 构建请求
            SettingsCardReq req = SettingsCardReq.newBuilder()
                .cardId(cardId)
                .settingsCardReqBody(SettingsCardReqBody.newBuilder()
                    .settings(settings.toString())
                    .sequence(sequence)
                    .build())
                .build();
            
            // 发送请求
            SettingsCardResp resp = feishuClient.cardkit().v1().card().settings(req);
            
            // 处理响应
            if (!resp.success()) {
                log.error("停止卡片流式更新失败: code=[{}], msg=[{}]", resp.getCode(), resp.getMsg());
                return false;
            }
            
            log.info("停止卡片流式更新成功");
            return true;
        } catch (Exception e) {
            log.error("停止卡片流式更新异常", e);
            return false;
        }
    }
} 