package em.backend.service.delegate.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import em.backend.mapper.CaseInfoMapper;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICardTemplateService;
import em.backend.service.IUserStatusService;
import em.backend.service.delegate.ICaseSelectDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 案件选择委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseSelectDelegateImpl implements ICaseSelectDelegate {

    private final CaseInfoMapper caseInfoMapper;
    private final IUserStatusService userStatusService;
    private final ICardTemplateService cardTemplateService;

    @Override
    public P2CardActionTriggerResponse handleSelectCase(String caseId, String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理选择案件请求: caseId={}, operatorId={}", caseId, operatorId);
            
            // 1. 查询案件信息
            CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
            if (caseInfo == null) {
                toast.setType("error");
                toast.setContent("案件不存在");
                resp.setToast(toast);
                return resp;
            }

            // 2. 更新用户状态
            UserStatus userStatus = new UserStatus();
            userStatus.setOpenId(operatorId);
            userStatus.setCurrentCaseId(caseInfo.getId());
            userStatus.setCurrentCaseName(caseInfo.getCaseName());

            if (!userStatusService.saveOrUpdate(userStatus)) {
                toast.setType("error");
                toast.setContent("更新用户状态失败");
                resp.setToast(toast);
                return resp;
            }

            // 3. 构建成功响应
            String cardContent = cardTemplateService.buildSelectSuccessCard(caseInfo.getCaseName());
            CallBackCard card = new CallBackCard();
            card.setType("raw");
            card.setData(JSONObject.parseObject(cardContent));
            resp.setCard(card);

            toast.setType("success");
            toast.setContent("已切换到案件：" + caseInfo.getCaseName());
            resp.setToast(toast);

            return resp;
        } catch (Exception e) {
            log.error("处理选择案件异常: caseId={}, operatorId={}", caseId, operatorId, e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }
} 