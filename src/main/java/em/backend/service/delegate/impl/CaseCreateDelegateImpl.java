package em.backend.service.delegate.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import em.backend.mapper.CaseInfoMapper;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICardTemplateService;
import em.backend.service.IFeishuFolderService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import em.backend.service.IDifyKnowledgeService;
import em.backend.service.delegate.ICaseCreateDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 案件创建委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseCreateDelegateImpl implements ICaseCreateDelegate {

    private final IFeishuFolderService folderService;
    private final IUserStatusService userStatusService;
    private final ICardTemplateService cardTemplateService;
    private final IDifyKnowledgeService difyKnowledgeService;
    private final CaseInfoMapper caseInfoMapper;
    private final IMessageService messageService;

    @Override
    public P2CardActionTriggerResponse handleCreateCaseForm(Map<String, Object> formData, String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理创建案件表单数据: {}, 操作人: {}", formData, operatorId);

            // 1. 获取表单数据
            String caseName = String.valueOf(formData.get("case_name"));
            String clientName = String.valueOf(formData.get("client_name"));
            String caseDesc = String.valueOf(formData.get("case_desc"));
            String caseTime = String.valueOf(formData.get("case_time"));
            String remarks = String.valueOf(formData.get("remarks"));

            // 验证必要字段
            if (caseName == null || clientName == null || caseTime == null) {
                toast.setType("error");
                toast.setContent("必要字段缺失");
                resp.setToast(toast);
                return resp;
            }

            // 2. 创建案件文件夹
            IFeishuFolderService.FolderResult folderResult = folderService.createCaseFolder(caseName, caseTime, clientName);
            if (folderResult == null) {
                toast.setType("error");
                toast.setContent("创建文件夹失败");
                resp.setToast(toast);
                return resp;
            }

            // 3. 设置文件夹权限
            if (!folderService.setFolderPermission(folderResult.getToken(), operatorId)) {
                toast.setType("error");
                toast.setContent("设置权限失败");
                resp.setToast(toast);
                return resp;
            }

            // 4. 创建Dify知识库
            String knowledgeDesc = String.format("案件描述：%s\n当事人：%s\n案件时间：%s",
                    caseDesc != null ? caseDesc : "无",
                    clientName,
                    caseTime);
            IDifyKnowledgeService.KnowledgeResult knowledgeResult = difyKnowledgeService.createKnowledge(
                    knowledgeDesc
            );

            if (knowledgeResult == null) {
                log.error("创建Dify知识库失败");
                // 不中断流程，继续保存案件信息
            }

            // 5. 保存案件信息到数据库
            CaseInfo caseInfo = new CaseInfo();
            caseInfo.setOpenId(operatorId);
            caseInfo.setCaseName(caseName);
            caseInfo.setClientName(clientName);
            caseInfo.setCaseDesc(caseDesc);
            caseInfo.setCaseTime(LocalDateTime.parse(caseTime.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            caseInfo.setRemarks(remarks);
            caseInfo.setFolderUrl(folderResult.getUrl());

            // 设置Dify知识库ID
            if (knowledgeResult != null) {
                caseInfo.setDifyKnowledgeId(knowledgeResult.getId());
            }

            if (caseInfoMapper.insert(caseInfo) <= 0) {
                toast.setType("error");
                toast.setContent("保存案件信息失败");
                resp.setToast(toast);
                return resp;
            }

            // 6. 更新用户状态
            UserStatus userStatus = new UserStatus();
            userStatus.setOpenId(operatorId);
            userStatus.setCurrentCaseId(caseInfo.getId());
            userStatus.setCurrentCaseName(caseInfo.getCaseName());

            if (!userStatusService.saveOrUpdate(userStatus)) {
                log.error("更新用户状态失败");
            }

            // 7. 构建成功响应
            String cardContent = cardTemplateService.buildCreateSuccessCard(caseName,folderResult.getUrl());
            CallBackCard card = new CallBackCard();
            card.setType("raw");
            card.setData(JSONObject.parseObject(cardContent));
            resp.setCard(card);

            toast.setType("success");
            toast.setContent("案件创建成功");
            resp.setToast(toast);

            return resp;
        } catch (Exception e) {
            log.error("处理创建案件表单失败", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }
    

    @Override
    public void sendCreateCaseCard(String openId) {
        try {
            String cardContent = cardTemplateService.buildCreateCaseCard();
            messageService.sendCardMessage(openId, cardContent);
        } catch (Exception e) {
            log.error("发送创建案件卡片失败: openId={}", openId, e);
        }
    }
} 