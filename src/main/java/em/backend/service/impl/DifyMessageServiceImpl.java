package em.backend.service.impl;

import em.backend.dify.IDifyClient;
import em.backend.dify.handler.FeishuStreamingMessageHandler;
import em.backend.mapper.UserGroupMapper;
import em.backend.pojo.UserGroup;
import em.backend.pojo.UserStatus;
import em.backend.service.IDifyMessageService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Dify消息服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyMessageServiceImpl implements IDifyMessageService {
    
    private final IDifyClient difyClient;
    private final IMessageService messageService;
    private final UserGroupMapper userGroupMapper;
    private final IUserStatusService userStatusService;

    @Override
    public boolean handleUserMessage(String openId, String chatId, String query) {
        log.info("处理用户消息: 用户=[{}], 查询=[{}]", openId, query);
        try {
            // 查询用户群组信息
            UserGroup userGroup = userGroupMapper.selectById(openId);
            String conversationId = (userGroup != null) ? userGroup.getChatId() : null;

            // 查询用户当前案件
            UserStatus userStatus = userStatusService.lambdaQuery()
                    .eq(UserStatus::getOpenId, openId)
                    .one();

            // 构建消息卡片
            String currentCase = userStatus != null && userStatus.getCurrentCaseName() != null
                    ? userStatus.getCurrentCaseName()
                    : "未选择案件";


            // 1. 创建并发送流式卡片
            String cardInfo = messageService.sendStreamingMessage(openId, "思考中...", currentCase);
            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                return false;
            }
            log.info("创建流式卡片成功: [{}]", cardInfo);
            
            // 2. 创建消息处理器并异步调用Dify流式API
            FeishuStreamingMessageHandler messageHandler = new FeishuStreamingMessageHandler(
                cardInfo, messageService);
            
            CompletableFuture.runAsync(() -> {
                try {
                    difyClient.chatStreaming(
                        query,          // 用户输入
                        null,           // 无额外变量
                        openId,         // 用户标识
                        messageHandler, // 消息处理器
                        conversationId, // 使用查询到的会话ID
                        null,           // 无文件
                        true            // 自动生成标题
                    );
                } catch (Exception e) {
                    log.error("调用Dify API失败: {}", e.getMessage());
                    messageHandler.onError(e);
                }
            });
            
            return true;
        } catch (Exception e) {
            log.error("处理用户消息失败: {}", e.getMessage());
            return false;
        }
    }
} 