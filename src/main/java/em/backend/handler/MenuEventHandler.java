package em.backend.handler;

import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.application.v6.model.P2BotMenuV6;
import em.backend.service.ICaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuEventHandler implements IEventHandler<P2BotMenuV6, Void> {
    
    private final ICaseService caseService;
    
    @Override
    public Void handle(P2BotMenuV6 event) {
        try {
            log.info("收到菜单事件: {}", Jsons.DEFAULT.toJson(event.getEvent()));
            
            String eventKey = event.getEvent().getEventKey();
            String openId = event.getEvent().getOperator().getOperatorId().getOpenId();
            
            // 根据菜单事件类型分发处理
            switch (eventKey) {
                case "create_case":
                    caseService.sendCreateCaseCard(openId);
                    break;
                    
                case "select_case":
                    caseService.sendSelectCaseCard(openId, "select_case_callback");
                    break;
                    
                case "case_overview":  // 新增案件总览事件
                    caseService.sendSelectCaseCard(openId, "case_overview_callback");
                    break;
                    
                default:
                    log.warn("未知的菜单事件: {}", eventKey);
            }
        } catch (Exception e) {
            log.error("处理菜单事件异常", e);
        }
        return null;
    }
} 