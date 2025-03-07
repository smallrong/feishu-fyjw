package em.backend.service.delegate;

import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import java.util.Map;

/**
 * 案件创建委托接口
 * 负责处理案件创建相关的业务逻辑
 */
public interface ICaseCreateDelegate {
    
    /**
     * 处理创建案件表单
     *
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleCreateCaseForm(Map<String, Object> formData, String operatorId);

    /**
     * 发送创建案件卡片
     *
     * @param openId 用户ID
     */
    void sendCreateCaseCard(String openId);

}