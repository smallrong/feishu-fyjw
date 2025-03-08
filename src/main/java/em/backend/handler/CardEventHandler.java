package em.backend.handler;

import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import em.backend.service.ICaseService;
import em.backend.service.delegate.ICaseLegalResearchDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardEventHandler implements IEventHandler<P2CardActionTrigger, P2CardActionTriggerResponse> {
    
    private final ICaseService caseService;
    private final ICaseLegalResearchDelegate caseLegalResearchDelegate;
    
    @Override
    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        
        try {
            log.info("收到卡片回调事件: {}", Jsons.DEFAULT.toJson(event));
            String openMessageId = event.getEvent().getContext().getOpenMessageId();
            log.info("消息ID: {}", openMessageId);
            
            String actionName = event.getEvent().getAction().getName();
            if(actionName == null || actionName.isEmpty()){
                actionName = event.getEvent().getAction().getValue().toString();
                log.info("actionName: {}", actionName);
            }
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
                    
                case "button_study_submit_no":
                    // 处理法律研究输入
                    log.info("法律研究输入: {}", formData);
                    return caseService.handleLegalResearchInput(formData, operatorId);
                    
                case "button_study_inquire_submit_ok":
                    // 处理法律研究查询确认
                    log.info("法律研究查询确认: {}", formData);
                    return caseLegalResearchDelegate.handleLegalResearchConfirm(operatorId);
                
                case "button_study_inquire_submit_no":
                    // 处理法律研究查询取消
                    log.info("法律研究查询取消: {}", formData);
                    return caseLegalResearchDelegate.handleLegalResearchCancel(formData, operatorId);
                    
                case "button_keyword_ok":
                    // 处理关键词确认，调用原有的法律研究输入处理
                    log.info("处理关键词确认: formData={}", formData);
                    return caseLegalResearchDelegate.handleLegalResearchInput(formData, operatorId);
                    
                case "button_strategy_analysis":
                    // 处理策略分析请求
                    log.info("策略分析请求: operatorId={}", operatorId);
                    return caseService.handleStrategyAnalysis(operatorId);
                    
                case "button_suggest_submit":
                    // 处理策略分析确认
                    log.info("策略分析确认: formData={}", formData);
                    return caseService.handleStrategyAnalysisConfirm(formData, operatorId,openMessageId);
                    
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