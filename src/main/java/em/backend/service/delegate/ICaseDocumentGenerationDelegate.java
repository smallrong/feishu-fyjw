package em.backend.service.delegate;

import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;

/**
 * 案件文书生成委托接口
 */
public interface ICaseDocumentGenerationDelegate {
    
    /**
     * 处理文书生成的通用方法
     * @param operatorId 操作人ID
     * @param documentType 文书类型
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleDocumentGeneration(String operatorId, String documentType);
} 