package em.backend.controller;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.application.ApplicationService;
import com.lark.oapi.service.application.v6.model.P2BotMenuV6;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import em.backend.handler.CardEventHandler;
import em.backend.handler.MenuEventHandler;
import em.backend.handler.MessageEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 飞书事件控制器
 * 负责接收飞书事件并分发到对应的处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuEventController {

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    private final MessageEventHandler messageEventHandler;
    private final MenuEventHandler menuEventHandler;
    private final CardEventHandler cardEventHandler;

    private EventDispatcher eventDispatcher;

    @PostConstruct
    public void init() {
        // 创建事件分发器
        eventDispatcher = EventDispatcher.newBuilder(appId, appSecret)
            // 处理消息事件
            .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                @Override
                public void handle(P2MessageReceiveV1 event) {
                    messageEventHandler.handle(event);
                }
            })
            // 处理菜单事件
            .onP2BotMenuV6(new ApplicationService.P2BotMenuV6Handler() {
                @Override
                public void handle(P2BotMenuV6 event) {
                    menuEventHandler.handle(event);
                }
            })
            // 处理卡片事件
            .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                @Override
                public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                    return cardEventHandler.handle(event);
                }
            })
            .build();

        // 启动WebSocket客户端
        com.lark.oapi.ws.Client wsClient = new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(eventDispatcher)
                .build();
        wsClient.start();
        log.info("飞书事件监听已启动");
    }
} 