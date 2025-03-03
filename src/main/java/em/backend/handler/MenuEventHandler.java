package em.backend.handler;

import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.application.v6.model.P2BotMenuV6;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICardTemplateService;
import em.backend.service.ICaseService;
import em.backend.service.IMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuEventHandler implements IEventHandler<P2BotMenuV6, Void> {

    private final ICaseService caseService;
    private final IMessageService messageService;
    private final ICardTemplateService cardTemplateService;

    @Override
    public Void handle(P2BotMenuV6 event) {
        try {
            log.info("收到菜单事件: {}", Jsons.DEFAULT.toJson(event.getEvent()));

            String eventKey = event.getEvent().getEventKey();
            String openId = event.getEvent().getOperator().getOperatorId().getOpenId();
            UserStatus fileClassStatus = caseService.getCurrentCase(openId);
            CaseInfo currentCaseInfo = caseService.getCurrentCaseInfo(fileClassStatus.getCurrentCaseId().toString());
            // 根据菜单事件类型分发处理
            switch (eventKey) {
                case "create_case":
                    caseService.sendCreateCaseCard(openId);
                    break;

                case "select_case":
                    caseService.sendSelectCaseCard(openId, "select_case_callback");
                    break;

                case "case_overview":  // 新增案件总览事件


                    if (fileClassStatus != null && fileClassStatus.getCurrentCaseId() != null) {
                        // 如果有当前案件，发送文件分类卡片
                        caseService.handleCaseOverview(String.valueOf(currentCaseInfo.getId()), openId);
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件再进行文件分类", openId);
                    }
                    break;
                    
                case "legal_research":  // 法律研究事件
                    // 直接发送法律研究卡片，不需要先选择案件
                    UserStatus userStatus = caseService.getCurrentCase(openId);
                    if (userStatus != null && userStatus.getCurrentCaseId() != null) {
                        caseService.sendLegalResearchCard(openId, userStatus.getCurrentCaseId().toString());
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件", openId);
                    }
                    break;
                    
                case "indictment_generation":  // 起诉状/答辩状生成事件
                    caseService.handleDocumentGeneration(openId, "起诉状/答辩状");
                    break;
                    
                case "application_generation":  // 申请书/答辩书生成事件
                    caseService.handleDocumentGeneration(openId, "申请书/答辩书");
                    break;
                    
                case "agent_speech_generation":  // 代理词生成事件
                    caseService.handleDocumentGeneration(openId, "代理词");
                    break;
                    
                case "case_statement":  // 案情陈述事件
                    // 检查当前案件
                    UserStatus caseStatementStatus = caseService.getCurrentCase(openId);
                    if (caseStatementStatus != null && caseStatementStatus.getCurrentCaseId() != null) {
                        // 直接调用案情陈述处理方法
                        caseService.handleCaseStatement(openId);
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件", openId);
                    }
                    break;
                    
                case "strategy_analysis":  // 策略分析事件
                    // 检查当前案件
                    UserStatus strategyStatus = caseService.getCurrentCase(openId);
                    if (strategyStatus != null && strategyStatus.getCurrentCaseId() != null) {
                        // 直接调用策略分析处理方法
                        caseService.handleStrategyAnalysis(openId);
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件", openId);
                    }
                    break;

                case "file_analysis":  // 文件分类事件

                    if (fileClassStatus != null && fileClassStatus.getCurrentCaseId() != null) {
                        // 如果有当前案件，发送文件分类卡片
                        caseService.sendFileClassificationCard(openId, fileClassStatus.getCurrentCaseId().toString(),currentCaseInfo.getDifyKnowledgeId());
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件再进行文件分类", openId);
                    }
                    break;
                case "file_list":
                    if (fileClassStatus != null && fileClassStatus.getCurrentCaseId() != null) {
                        // 如果有当前案件，发送文件分类卡片
                        messageService.sendCardMessage(openId, cardTemplateService.buildMessageCard("您上传的文件在以下连接，请点击后查询[点击查看](https://vcnjyexq8awr.feishu.cn/wiki/O2NQwLMRDiOciKk6RV0cMbbQnwg?from=from_copylink)", fileClassStatus.getCurrentCaseName()));
                    } else {
                        // 如果没有当前案件，提示用户先选择案件
                        messageService.sendMessage(openId, "请先选择一个案件再进行文件分类", openId);
                    }
                default:
                    log.warn("未知的菜单事件: {}", eventKey);
            }
        } catch (Exception e) {
            log.error("处理菜单事件异常", e);
        }
        return null;
    }
} 