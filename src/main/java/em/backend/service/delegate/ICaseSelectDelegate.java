package em.backend.service.delegate;

import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;

/**
 * 案件选择委托接口
 * 负责处理案件选择相关的业务逻辑
 */
public interface ICaseSelectDelegate {
    
    /**
     * 处理选择案件
     *
     * @param caseId     案件ID
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleSelectCase(String caseId, String operatorId);
} 