package em.backend.service.delegate;

import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import java.util.Map;

/**
 * 案件法律研究委托接口
 * 负责处理案件法律研究相关的业务逻辑
 */
public interface ICaseLegalResearchDelegate {
    
    /**
     * 处理法律研究输入
     *
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleLegalResearchInput(Map<String, Object> formData, String operatorId);

    /**
     * 发送法律研究卡片
     *
     * @param openId 用户ID
     * @param caseId 案件ID
     */
    void sendLegalResearchCard(String openId, String caseId);
} 