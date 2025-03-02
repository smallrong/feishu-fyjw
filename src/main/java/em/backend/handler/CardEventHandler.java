package em.backend.handler;

import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import em.backend.service.ICaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardEventHandler implements IEventHandler<P2CardActionTrigger, P2CardActionTriggerResponse> {
    
    private final ICaseService caseService;
    
    @Override
    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        
        try {
            log.info("收到卡片回调事件: {}", Jsons.DEFAULT.toJson(event));
            
            String actionName = event.getEvent().getAction().getName();
            Map<String, Object> formData = event.getEvent().getAction().getFormValue();
            String operatorId = event.getEvent().getOperator().getOpenId();

            if (actionName == null) {
                return null; // 不需要处理的事件
            }

            // 根据按钮动作处理
            switch (actionName) {
                case "submit_create_case":
                    // 处理案件创建表单提交
                    return caseService.handleCreateCaseForm(formData, operatorId);
                    
                case "select_case_callback":
                    // 处理案件选择
                    String selectedCaseId = String.valueOf(formData.get("Select_case"));
                    log.info("选择的案件ID: {}", selectedCaseId);
                    return caseService.handleSelectCase(selectedCaseId, operatorId);
                    
                case "case_overview_callback":
                    // 处理案件总览
                    String caseId = String.valueOf(formData.get("Select_case"));
                    log.info("案件总览选择的案件ID: {}", caseId);
                    return caseService.handleCaseOverview(caseId, operatorId);
                    
                case "legal_research_callback":
                    // 处理法律研究
                    String legalCaseId = String.valueOf(formData.get("Select_case"));
                    log.info("法律研究选择的案件ID: {}", legalCaseId);
                    return caseService.handleLegalResearch(legalCaseId, operatorId);
                    
                case "button_study_submit":
                    // 处理法律研究输入
                    log.info("法律研究输入: {}", formData);
                    return caseService.handleLegalResearchInput(formData, operatorId);
                    
                default:
                    log.warn("未知的按钮动作: {}", actionName);
                    toast.setType("warning");
                    toast.setContent("未知的操作");
                    resp.setToast(toast);
                    return resp;
            }
        } catch (Exception e) {
            log.error("处理卡片回调失败", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }
} 