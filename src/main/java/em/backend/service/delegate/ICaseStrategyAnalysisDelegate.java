package em.backend.service.delegate;

import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import java.util.Map;

/**
 * 案件策略分析委托接口
 */
public interface ICaseStrategyAnalysisDelegate {
    
    /**
     * 处理策略分析请求
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleStrategyAnalysis(String operatorId);

    /**
     * 处理策略分析确认请求
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @param cardId 卡片ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleStrategyAnalysisConfirm(Map<String, Object> formData, String operatorId, String cardId);
} 