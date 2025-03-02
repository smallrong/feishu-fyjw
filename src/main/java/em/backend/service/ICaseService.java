package em.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import java.util.List;
import java.util.Map;

/**
 * 案件服务接口
 */
public interface ICaseService extends IService<CaseInfo> {
    /**
     * 处理创建案件表单
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleCreateCaseForm(Map<String, Object> formData, String operatorId);

    /**
     * 处理选择案件
     * @param caseId 案件ID
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleSelectCase(String caseId, String operatorId);

    /**
     * 获取用户当前案件
     * @param openId 用户ID
     * @return 用户状态
     */
    UserStatus getCurrentCase(String openId);

    /**
     * 发送创建案件卡片
     * @param openId 用户ID
     */
    void sendCreateCaseCard(String openId);

    /**
     * 发送选择案件卡片
     * @param openId 用户ID
     * @param callbackName 回调方法名
     */
    void sendSelectCaseCard(String openId, String callbackName);

    /**
     * 处理案件总览
     * @param caseId 案件ID
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleCaseOverview(String caseId, String operatorId);
    
    /**
     * 发送法律研究卡片
     * @param openId 用户ID
     * @param caseId 案件ID
     */
    void sendLegalResearchCard(String openId, String caseId);
    
    /**
     * 处理法律研究输入
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    P2CardActionTriggerResponse handleLegalResearchInput(Map<String, Object> formData, String operatorId);
}