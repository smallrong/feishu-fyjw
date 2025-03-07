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

    /**
     * 处理法律研究菜单事件
     * 检查当前案件状态并发送相应的法律研究卡片
     *
     * @param openId 用户ID
     */
    void handleLegalResearchMenuEvent(String openId);

    /**
     * 处理法律研究取消事件
     * 执行新的工作流并发送流式响应
     *
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleLegalResearchCancel(Map<String, Object> formData, String operatorId,String context);

    /**
     * 处理法律研究确认事件
     * 创建新的对话组并更新用户状态
     *
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleLegalResearchConfirm(String operatorId);

    /**
     * 处理基于历史对话上下文的法律研究请求
     * 查询历史消息并作为上下文传递给处理函数
     *
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleLegalResearchWithContext(Map<String, Object> formData, String operatorId);

    /**
     * 处理法律研究退出事件
     * 清除用户的法律研究状态并发送退出成功的通知
     *
     * @param operatorId 操作人ID
     * @return 卡片回调响应，包含退出成功的Toast消息
     */
    P2CardActionTriggerResponse handleLegalResearchLogout(String operatorId);
} 