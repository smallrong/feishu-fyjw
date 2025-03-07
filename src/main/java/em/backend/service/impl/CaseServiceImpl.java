package em.backend.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lark.oapi.Client;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import em.backend.dify.IDifyClient;
import em.backend.dify.model.workflow.WorkflowChunkResponse;
import em.backend.mapper.CaseInfoMapper;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICaseService;
import em.backend.service.ICardTemplateService;
import em.backend.service.IFeishuFolderService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import em.backend.service.IDifyKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import em.backend.util.MarkdownToWordConverter;
import java.io.File;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Paths;
import em.backend.common.CardTemplateConstants;
import em.backend.service.delegate.ICaseCreateDelegate;
import em.backend.service.delegate.ICaseSelectDelegate;
import em.backend.service.delegate.ICaseLegalResearchDelegate;
import em.backend.service.delegate.ICaseStrategyAnalysisDelegate;
import em.backend.service.delegate.ICaseDocumentGenerationDelegate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl extends ServiceImpl<CaseInfoMapper, CaseInfo> implements ICaseService {

    /**
     * 法律研究工作流API Key
     */
    private static final String LEGAL_RESEARCH_WORKFLOW_KEY = "app-CuItMOSRz9WGVoQCGyeEDbKC";

    /**
     * 案由分析工作流API Key
     */
    private static final String CAUSE_ANALYSIS_WORKFLOW_KEY = "app-d9ElIv3eDGpD1Qf6pOp4fLF4";

    /**
     * 策略分析工作流API Key
     */
    private static final String STRATEGY_ANALYSIS_WORKFLOW_KEY = "app-tuF5jDloKzk59sDgJnOjLhTl";

    /**
     * 文书生成工作流API Key
     */
    private static final String DOCUMENT_GENERATION_WORKFLOW_KEY = "app-EOeMAYUjkIHI1bIluo8wP5MC";

    /**
     * 案情陈述工作流API Key
     */
    private static final String CASE_STATEMENT_WORKFLOW_KEY = "app-6QQh84PayZnD2GDL1lfVtNNs";
     
    /**
     * 法律研究聊天API Key
     */
    public static final String LEGAL_RESEARCH_CHAT_KEY = "app-apBHFUFvPbfY47LTUrvliMLL";
    

    private final IFeishuFolderService folderService;
    private final IUserStatusService userStatusService;
    private final IMessageService messageService;
    private final ICardTemplateService cardTemplateService;
    private final Client feishuClient;
    private final IDifyClient difyClient;
    private final IDifyKnowledgeService difyKnowledgeService;
    private final ICaseCreateDelegate caseCreateDelegate;
    private final ICaseSelectDelegate caseSelectDelegate;
    private final ICaseLegalResearchDelegate caseLegalResearchDelegate;
    private final ICaseStrategyAnalysisDelegate caseStrategyAnalysisDelegate;
    private final ICaseDocumentGenerationDelegate caseDocumentGenerationDelegate;
    


    @Override
    public P2CardActionTriggerResponse handleCreateCaseForm(Map<String, Object> formData, String operatorId) {
        return caseCreateDelegate.handleCreateCaseForm(formData, operatorId);
    }

    @Override
    public P2CardActionTriggerResponse handleSelectCase(String caseId, String operatorId) {
        return caseSelectDelegate.handleSelectCase(caseId, operatorId);
    }

    @Override
    public UserStatus getCurrentCase(String openId) {
        return userStatusService.lambdaQuery()
                .eq(UserStatus::getOpenId, openId)
                .one();
    }
    @Override
    public CaseInfo getCurrentCaseInfo(String id) {
        return getById(id);
    }

    @Override
    public void sendCreateCaseCard(String openId) {
        caseCreateDelegate.sendCreateCaseCard(openId);
    }

    @Override
    public void sendSelectCaseCard(String openId, String callbackName) {
        try {
            // 1. 查询用户的所有案件
            List<CaseInfo> cases = lambdaQuery()
                    .eq(CaseInfo::getOpenId, openId)
                    .orderByDesc(CaseInfo::getCreateTime)
                    .list();

            if (cases.isEmpty()) {
                messageService.sendCardMessage(openId, cardTemplateService.buildErrorMessageCard("您还没有创建任何案件", "当前未选择案件"));
                return;
            }

            // 2. 查询用户当前案件
            UserStatus userStatus = getCurrentCase(openId);
            String currentCase = userStatus != null && userStatus.getCurrentCaseName() != null
                    ? userStatus.getCurrentCaseName()
                    : "选择案件";

            // 3. 构建并发送卡片
            String cardContent = cardTemplateService.buildSelectCaseCard(currentCase, cases, callbackName);
            messageService.sendCardMessage(openId, cardContent);
        } catch (Exception e) {
            log.error("发送选择案件卡片失败: openId={}", openId, e);
        }
    }

    @Override
    public P2CardActionTriggerResponse handleCaseOverview(String caseId, String operatorId) {
        try {
            // 1. 先执行选择案件逻辑
            P2CardActionTriggerResponse selectResp = handleSelectCase(caseId, operatorId);

            // 2. 额外发送案件总览卡片
            try {
                // 查询案件信息
                CaseInfo caseInfo = getById(caseId);
                if (caseInfo == null) {
                    log.error("案件不存在: {}", caseId);
                    return selectResp;
                }

                // 构建Markdown内容
                String markdownContent = generateCaseSummaryContent(caseInfo);

                // 构建案件总览卡片
                String cardContent = cardTemplateService.buildCaseSummaryCard(
                        caseInfo.getCaseName(), markdownContent);

                // 使用消息服务发送卡片
                //保持代码的清晰和可维护性
                messageService.sendCardMessage(operatorId, cardContent);

            } catch (Exception e) {
                log.error("发送案件总览卡片失败", e);
                // 发送失败不影响原有流程
            }

            // 返回原有的选择案件响应
            return selectResp;
        } catch (Exception e) {
            log.error("处理案件总览异常: caseId={}, operatorId={}", caseId, operatorId, e);
            CallBackToast toast = new CallBackToast();
            toast.setType("error");
            toast.setContent("系统处理失败");
            P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
            resp.setToast(toast);
            return resp;
        }
    }

    /**
     * 生成案件总览的Markdown内容 后续需要替换为实际的数据
     *
     * @param caseInfo 案件信息
     * @return Markdown格式的内容
     */
    // TODO 后续需要替换为实际的数据
    private String generateCaseSummaryContent(CaseInfo caseInfo) {
        StringBuilder sb = new StringBuilder();

        // 添加案件基本信息
        sb.append("### 案件基本信息\n");
        sb.append("- 案件名称: ").append(caseInfo.getCaseName()).append("\n");
        sb.append("- 委托人: ").append(caseInfo.getClientName()).append("\n");

        // 添加案件描述
        if (caseInfo.getCaseDesc() != null && !caseInfo.getCaseDesc().isEmpty()) {
            sb.append("\n- 案件描述:\n");
            sb.append(caseInfo.getCaseDesc()).append("\n");
        }

        // 添加案件时间
        if (caseInfo.getCaseTime() != null) {
            sb.append("\n- 案件时间 : ").append(caseInfo.getCaseTime()).append("\n");
        }

        // 添加备注
        if (caseInfo.getRemarks() != null && !caseInfo.getRemarks().isEmpty()) {
            sb.append("\n- 备注\n");
            sb.append(caseInfo.getRemarks()).append("\n");
        }

        // 添加文件夹链接
        if (caseInfo.getFolderUrl() != null && !caseInfo.getFolderUrl().isEmpty()) {
            sb.append("\n- 案件文件夹\n");
            sb.append("[点击查看案件文件夹](").append(caseInfo.getFolderUrl()).append(")\n");
        }

        return sb.toString();
    }

    @Override
    public void sendLegalResearchCard(String openId, String caseId) {
        caseLegalResearchDelegate.sendLegalResearchCard(openId, caseId);
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchInput(Map<String, Object> formData, String operatorId) {
        return caseLegalResearchDelegate.handleLegalResearchInput(formData, operatorId);
    }

    /**
     * 处理策略分析请求
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleStrategyAnalysis(String operatorId) {
        return caseStrategyAnalysisDelegate.handleStrategyAnalysis(operatorId);
    }

    /**
     * 处理策略分析确认请求
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleStrategyAnalysisConfirm(Map<String, Object> formData, String operatorId, String cardId) {
        return caseStrategyAnalysisDelegate.handleStrategyAnalysisConfirm(formData, operatorId, cardId);
    }

    /**
     * 处理文书生成的通用方法
     * @param operatorId 操作人ID
     * @param documentType 文书类型
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleDocumentGeneration(String operatorId, String documentType) {
        return caseDocumentGenerationDelegate.handleDocumentGeneration(operatorId, documentType);
    }

    @Override
    public void sendFileClassificationCard(String openId, String caseId,String difyKnowledgeId) {
        try {
            // 查询案件信息
            CaseInfo caseInfo = getById(caseId);
            if (caseInfo == null) {
                log.error("案件不存在: {}", caseId);
                messageService.sendMessage(openId, "案件不存在", openId);
                return;
            }

            // 获取案件文件夹中的文件列表
            String folderToken = extractFolderToken(caseInfo.getFolderUrl());
            log.info("获取文件夹token: {}", folderToken);
            if (folderToken == null) {
                log.error("无法获取文件夹token: {}", caseInfo.getFolderUrl());
                messageService.sendMessage(openId, "无法获取文件列表", openId);
                return;
            }

//            cardTemplateService.buildMessageCard(openId, caseId, folderToken);
            String documentType = "文件生成";
            String cardTitle = documentType + ": " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(openId, "正在处理" + documentType + "生成请求，请稍候...", cardTitle);

            if (cardInfo == null) {
                log.error("创建流式卡片失败");
            }
            log.info("创建{}流式卡片成功: cardInfo={}", documentType, cardInfo);

            folderService.analyzeFilesAsync(folderToken,openId,caseId,caseInfo.getCaseName(),difyKnowledgeId,cardInfo);
        } catch (Exception e) {
            log.error("发送文件分类卡片失败: openId={}, caseId={}", openId, caseId, e);
            messageService.sendMessage(openId, "发送文件分类卡片失败", openId);
        }
    }

    /**
     * 从文件夹URL中提取token
     */
    private String extractFolderToken(String folderUrl) {
        if (folderUrl == null || folderUrl.isEmpty()) {
            return null;
        }
        // 示例URL: https://xxx.feishu.cn/drive/folder/xxx
        String[] parts = folderUrl.split("/");
        return parts[parts.length - 1];
    }

    /**
     * 处理法律研究输入V2
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleLegalResearchInputV2(Map<String, Object> formData, String operatorId) {
        // 根据用户要求，暂不实现此方法
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        toast.setType("error");
        toast.setContent("此功能尚未实现");
        resp.setToast(toast);
        return resp;
    }

    /**
     * 处理案情陈述请求
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleCaseStatement(String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理案情陈述请求: operatorId={}", operatorId);
            
            // 1. 获取当前案件信息
            UserStatus userStatus = getCurrentCase(operatorId);
            if (userStatus == null || userStatus.getCurrentCaseId() == null) {
                log.error("未找到当前案件");
                toast.setType("error");
                toast.setContent("未找到当前案件，请先选择一个案件");
                resp.setToast(toast);
                return resp;
            }
            
            CaseInfo caseInfo = getById(userStatus.getCurrentCaseId());
            if (caseInfo == null) {
                log.error("案件不存在: {}", userStatus.getCurrentCaseId());
                toast.setType("error");
                toast.setContent("案件不存在");
                resp.setToast(toast);
                return resp;
            }
            
            // 2. 创建并发送流式卡片
            String cardTitle = "案情陈述: " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(operatorId, "正在生成案情陈述，请稍候...", cardTitle);
            
            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                toast.setType("error");
                toast.setContent("创建流式卡片失败");
                resp.setToast(toast);
                return resp;
            }
            
            log.info("创建案情陈述流式卡片成功: cardInfo={}", cardInfo);
            
            // 3. 准备工作流输入参数
            Map<String, Object> inputs = new HashMap<>();
            
            // 构建案件基本信息
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("案件名称: ").append(caseInfo.getCaseName());
            
            if (caseInfo.getClientName() != null && !caseInfo.getClientName().isEmpty()) {
                contentBuilder.append("\n委托人: ").append(caseInfo.getClientName());
            }
            
            if (caseInfo.getCaseDesc() != null && !caseInfo.getCaseDesc().isEmpty()) {
                contentBuilder.append("\n案件描述: ").append(caseInfo.getCaseDesc());
            }
            
            inputs.put("content", contentBuilder.toString());
            
            // 检查是否有知识库ID，如果有则添加到输入参数中
            if (caseInfo.getDifyKnowledgeId() != null && !caseInfo.getDifyKnowledgeId().isEmpty()) {
                log.info("案件关联知识库ID: {}", caseInfo.getDifyKnowledgeId());
                inputs.put("knowledge_id", caseInfo.getDifyKnowledgeId());
            } else {
                log.info("案件未关联知识库");
            }
            
            // 4. 创建工作流消息处理器
            final int[] sequence = {2}; // 序列号从2开始，因为初始消息已经是1
            final StringBuilder resultContent = new StringBuilder();
            
            Consumer<WorkflowChunkResponse> messageHandler = chunk -> {
                try {
                    if (chunk == null) {
                        return;
                    }
                    
                    log.debug("收到工作流事件: event={}, taskId={}", 
                            chunk.getEvent(), chunk.getTask_id());
                    
                    // 根据事件类型处理
                    if (chunk.isWorkflowStarted()) {
                        // 工作流开始
                        messageService.updateStreamingContent(cardInfo, 
                                "正在开始生成案情陈述，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        // 节点开始
                        messageService.updateStreamingContent(cardInfo, 
                                "正在分析案件信息，生成案情陈述...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        // 节点完成
                        messageService.updateStreamingContent(cardInfo, 
                                "正在整理案情陈述内容，请稍候...", sequence[0]++);
                    } else if (chunk.isWorkflowFinished()) {
                        // 工作流完成
                        if (chunk.getData() != null) {
                            // 从工作流输出中提取结果
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            log.debug("工作流完成数据: {}", dataObj);
                            
                            if (dataObj.containsKey("outputs")) {
                                JSONObject outputs = dataObj.getJSONObject("outputs");
                                // 根据实际输出格式，从text字段获取结果
                                if (outputs.containsKey("text")) {
                                    resultContent.append(outputs.getString("text"));
                                }
                            }
                        }
                        
                        // 获取最终结果
                        String finalContent = resultContent.length() > 0 
                                ? resultContent.toString() 
                                : "案情陈述生成完成，但未获取到结果。";
                                
                        // 更新最终结果
                        messageService.updateStreamingContent(cardInfo, 
                                finalContent, sequence[0]++);
                        
                        // 生成Word文档并发送
                        try {
                            if (resultContent.length() > 0) {
                                // 生成文件名
                                String fileName = caseInfo.getCaseName() + "-案情陈述";
                                
                                // 将Markdown转换为Word文档
                                File wordFile = MarkdownToWordConverter.convertToWord(
                                        resultContent.toString(), fileName);
                                
                                // 上传文件到飞书
                                String fileKey = folderService.uploadFile(wordFile, fileName.replaceAll("[\\\\/:*?\"<>|]", "_") + ".docx", "docx");
                                
                                if (fileKey != null) {
                                    // 发送文件消息
                                    boolean sent = messageService.sendFileMessage(operatorId, fileKey, fileName.replaceAll("[\\\\/:*?\"<>|]", "_") + ".docx");
                                    
                                    if (sent) {
                                        messageService.updateStreamingContent(cardInfo, 
                                                finalContent + "\n\nWord文档已生成并发送，请查收。", sequence[0]++);
                                    } else {
                                        messageService.updateStreamingContent(cardInfo, 
                                                finalContent + "\n\nWord文档生成成功，但发送失败，请重试。", sequence[0]++);
                                    }
                                } else {
                                    messageService.updateStreamingContent(cardInfo, 
                                            finalContent + "\n\nWord文档生成成功，但上传失败，请重试。", sequence[0]++);
                                }
                                
                                // 删除临时文件
                                wordFile.delete();
                            }
                        } catch (Exception e) {
                            log.error("生成或发送Word文档失败", e);
                            messageService.updateStreamingContent(cardInfo, 
                                    finalContent + "\n\nWord文档生成失败: " + e.getMessage(), sequence[0]++);
                        }
                        
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                        
                        log.info("案情陈述工作流执行完成: workflowRunId={}", chunk.getWorkflow_run_id());
                    } else if (chunk.isPing()) {
                        // Ping事件，不需要特殊处理
                    } else {
                        // 其他事件
                        log.debug("未处理的工作流事件类型: {}", chunk.getEvent());
                    }
                } catch (Exception e) {
                    log.error("处理工作流消息异常", e);
                }
            };
            
            // 5. 异步执行工作流
            CompletableFuture.runAsync(() -> {
                try {
                    // 调用Dify客户端执行工作流
                    difyClient.runWorkflowStreaming(
                            inputs,         // 输入参数
                            operatorId,     // 用户ID
                            messageHandler, // 消息处理器
                            null,           // 无文件
                            CASE_STATEMENT_WORKFLOW_KEY  // 使用指定的API Key
                    );
                } catch (Exception e) {
                    log.error("执行案情陈述工作流异常", e);
                    try {
                        // 更新错误信息
                        messageService.updateStreamingContent(cardInfo, 
                                "案情陈述生成过程中发生错误: " + e.getMessage(), sequence[0]++);
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });
            
            // 6. 返回成功响应
            toast.setType("success");
            toast.setContent("案情陈述生成请求已提交，请等待结果");
            resp.setToast(toast);
            return resp;
            
        } catch (Exception e) {
            log.error("处理案情陈述异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }
} 