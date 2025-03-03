package em.backend.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl extends ServiceImpl<CaseInfoMapper, CaseInfo> implements ICaseService {

    private final IFeishuFolderService folderService;
    private final IUserStatusService userStatusService;
    private final IMessageService messageService;
    private final ICardTemplateService cardTemplateService;
    private final Client feishuClient;
    private final IDifyClient difyClient;
    private final IDifyKnowledgeService difyKnowledgeService;

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

            if (!save(caseInfo)) {
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
            String cardContent = cardTemplateService.buildCreateSuccessCard(folderResult.getUrl());
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
    public P2CardActionTriggerResponse handleSelectCase(String caseId, String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            // 1. 查询案件信息
            CaseInfo caseInfo = getById(caseId);
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
            log.error("处理案件选择异常: caseId={}, operatorId={}", caseId, operatorId, e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    @Override
    public UserStatus getCurrentCase(String openId) {
        return userStatusService.lambdaQuery()
                .eq(UserStatus::getOpenId, openId)
                .one();
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
        try {
            // 查询案件信息
            CaseInfo caseInfo = getById(caseId);
            if (caseInfo == null) {
                log.error("案件不存在: {}", caseId);
                messageService.sendMessage(openId, "案件不存在", openId);
                return;
            }

            // 构建并发送法律研究卡片
            String cardContent = cardTemplateService.buildLegalResearchCard(caseInfo.getCaseName());
            messageService.sendCardMessage(openId, cardContent);

            log.info("发送法律研究卡片成功: openId={}, caseId={}", openId, caseId);
        } catch (Exception e) {
            log.error("发送法律研究卡片失败: openId={}, caseId={}", openId, caseId, e);
        }
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchInput(Map<String, Object> formData, String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理法律研究输入: formData={}, operatorId={}", formData, operatorId);

            // 1. 获取用户输入的研究内容
            String studyInput = String.valueOf(formData.get("input_study"));
            log.info("用户输入的法律研究内容: {}", studyInput);

            // 2. 获取当前案件信息
            UserStatus userStatus = getCurrentCase(operatorId);
            if (userStatus == null || userStatus.getCurrentCaseId() == null) {
                log.error("未找到当前案件");
                toast.setType("error");
                toast.setContent("未找到当前案件");
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

            // 3. 创建并发送流式卡片
            String cardTitle = "法律研究: " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(operatorId, "正在处理法律研究请求，请稍候...", cardTitle);

            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                toast.setType("error");
                toast.setContent("创建流式卡片失败");
                resp.setToast(toast);
                return resp;
            }

            log.info("创建法律研究流式卡片成功: cardInfo={}", cardInfo);

            // 4. 准备工作流输入参数
            Map<String, Object> inputs = new HashMap<>();
            // 拼接用户输入和案件信息
            String researchQuery = studyInput + "\n案件名称: " + caseInfo.getCaseName();
            if (caseInfo.getCaseDesc() != null && !caseInfo.getCaseDesc().isEmpty()) {
                researchQuery += "\n案件描述: " + caseInfo.getCaseDesc();
            }
            
            inputs.put("content", researchQuery);
            
            // 检查是否有知识库ID，如果有则添加到输入参数中
            if (caseInfo.getDifyKnowledgeId() != null && !caseInfo.getDifyKnowledgeId().isEmpty()) {
                log.info("案件关联知识库ID: {}", caseInfo.getDifyKnowledgeId());
                inputs.put("knowledge_id", caseInfo.getDifyKnowledgeId());
            } else {
                log.info("案件未关联知识库");
            }
            
            // 5. 创建工作流消息处理器
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
                                "正在开始法律研究，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        // 节点开始
                        messageService.updateStreamingContent(cardInfo,
                                "正在分析案件信息，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        // 节点完成
                        messageService.updateStreamingContent(cardInfo,
                                "正在整理研究结果，请稍候...", sequence[0]++);
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

                        // 更新最终结果
                        String finalContent = resultContent.length() > 0
                                ? resultContent.toString()
                                : "法律研究完成，但未获取到结果。";

                        messageService.updateStreamingContent(cardInfo, finalContent, sequence[0]++);

                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);

                        log.info("法律研究工作流执行完成: workflowRunId={}", chunk.getWorkflow_run_id());
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

            // 6. 异步执行工作流
            CompletableFuture.runAsync(() -> {
                try {
                    // 调用Dify客户端执行工作流
                    difyClient.runWorkflowStreaming(
                            inputs,         // 输入参数
                            operatorId,     // 用户ID
                            messageHandler, // 消息处理器
                            null,           // 无文件
                            "app-CuItMOSRz9WGVoQCGyeEDbKC"  // 使用指定的API Key
                    );
                } catch (Exception e) {
                    log.error("执行法律研究工作流异常", e);
                    try {
                        // 更新错误信息
                        messageService.updateStreamingContent(cardInfo,
                                "法律研究过程中发生错误: " + e.getMessage(), sequence[0]++);
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });

            // 7. 返回成功响应
            toast.setType("success");
            toast.setContent("法律研究请求已提交，请等待结果");
            resp.setToast(toast);
            return resp;

        } catch (Exception e) {
            log.error("处理法律研究输入异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    /**
     * 处理策略分析请求
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleStrategyAnalysis(String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理策略分析请求: operatorId={}", operatorId);
            
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
            String cardTitle = "策略分析: " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(operatorId, "正在分析案件，生成案由建议，请稍候...", cardTitle);
            
            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                toast.setType("error");
                toast.setContent("创建流式卡片失败");
                resp.setToast(toast);
                return resp;
            }
            
            log.info("创建策略分析流式卡片成功: cardInfo={}", cardInfo);
            
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
                                "正在开始分析案件，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        // 节点开始
                        messageService.updateStreamingContent(cardInfo, 
                                "正在分析案件信息，生成案由建议...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        // 节点完成
                        messageService.updateStreamingContent(cardInfo, 
                                "正在整理分析结果，请稍候...", sequence[0]++);
                    } else if ("message_received".equals(chunk.getEvent())) {
                        // 收到消息块
                        if (chunk.getData() != null) {
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            if (dataObj.containsKey("message") && dataObj.getJSONObject("message").containsKey("content")) {
                                // 追加消息内容
                                String content = dataObj.getJSONObject("message").getString("content");
                                resultContent.append(content);
                                
                                // 更新流式卡片内容
                                messageService.updateStreamingContent(cardInfo, 
                                        resultContent.toString(), sequence[0]++);
                            }
                        }
                    } else if (chunk.isWorkflowFinished()) {
                        // 工作流完成
                        if (chunk.getData() != null) {
                            // 从工作流输出中提取结果
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            log.debug("工作流完成数据: {}", dataObj);
                            
                            if (dataObj.containsKey("outputs")) {
                                JSONObject outputs = dataObj.getJSONObject("outputs");
                                // 根据实际输出格式，从text字段获取结果
                                if (outputs.containsKey("text") && resultContent.length() == 0) {
                                    resultContent.append(outputs.getString("text"));
                                }
                            }
                        }
                        
                        // 获取最终结果
                        String finalContent = resultContent.length() > 0 
                                ? resultContent.toString() 
                                : "案由分析完成，但未获取到结果。";
                                
                        messageService.updateStreamingContent(cardInfo, 
                                "分析完成，正在生成确认卡片...", sequence[0]++);
                        
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                        // 发送确认卡片
                        try {
                            // 构建确认卡片参数
                            Map<String, Object> cardParams = new HashMap<>();
                            cardParams.put("case", caseInfo.getCaseName());
                            cardParams.put("cause_of_action", finalContent);
                            
                            // 使用模板ID: AAqB7jQNhNmcc
                            String cardContent = cardTemplateService.buildTemplateCard("AAqB7jQNhNmcc", cardParams);
                            messageService.sendCardMessage(operatorId, cardContent);
                            
                            log.info("发送案由确认卡片成功");
                        } catch (Exception e) {
                            log.error("发送案由确认卡片失败", e);
                            // 如果发送确认卡片失败，发送普通消息
                            messageService.sendCardMessage(operatorId, 
                                    cardTemplateService.buildMessageCard("案由分析结果: " + finalContent, caseInfo.getCaseName()));
                        }
                        
                        log.info("策略分析工作流执行完成: workflowRunId={}", chunk.getWorkflow_run_id());
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
                            "app-d9ElIv3eDGpD1Qf6pOp4fLF4"  // 使用指定的API Key
                    );
                } catch (Exception e) {
                    log.error("执行策略分析工作流异常", e);
                    try {
                        // 更新错误信息
                        messageService.updateStreamingContent(cardInfo, 
                                "策略分析过程中发生错误: " + e.getMessage(), sequence[0]++);
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });
            
            // 6. 返回成功响应
            toast.setType("success");
            toast.setContent("策略分析请求已提交，请等待结果");
            resp.setToast(toast);
            return resp;
            
        } catch (Exception e) {
            log.error("处理策略分析异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    /**
     * 处理策略分析确认请求
     * @param formData 表单数据
     * @param operatorId 操作人ID
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleStrategyAnalysisConfirm(Map<String, Object> formData, String operatorId,String cardId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理策略分析确认请求: operatorId={}, formData={}", operatorId, formData);
            
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
            
            // 2. 从表单数据中获取确认的案由
            String confirmedCauseOfAction = "";
            if (formData.containsKey("content")) {
                confirmedCauseOfAction = String.valueOf(formData.get("content"));
            } else {
                log.error("表单数据中未找到案由信息");
                toast.setType("error");
                toast.setContent("未找到案由信息，请重新提交");
                resp.setToast(toast);
                return resp;
            }
            
            log.info("确认的案由: {}", confirmedCauseOfAction);
            
            // 3. 首先更新卡片为选择成功
            try {
                // 构建确认成功卡片参数
                Map<String, Object> cardParams = new HashMap<>();
                cardParams.put("case", caseInfo.getCaseName());
                
                // 使用模板ID: AAqB7FSv59taw
                String cardContent = cardTemplateService.buildTemplateCard("AAqB7FSv59taw", cardParams);
                
                // 获取原卡片ID并更新
                if (!cardId.isEmpty()) {
//                     messageService.updateCardContent(cardId, cardContent);
//                     log.info("更新卡片成功: cardId={}", cardId);
                } else {
                    // 如果没有卡片ID，发送新卡片
                    // messageService.sendCardMessage(operatorId, cardContent);
                    // log.info("发送确认成功卡片成功");
                }
            } catch (Exception e) {
                log.error("更新卡片失败", e);
                // 更新失败不影响后续流程
            }
            
            // 4. 创建并发送流式卡片
            String cardTitle = "策略分析: " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(operatorId, "正在基于确认的案由生成策略分析，请稍候...", cardTitle);
            
            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                toast.setType("error");
                toast.setContent("创建流式卡片失败");
                resp.setToast(toast);
                return resp;
            }
            
            log.info("创建策略分析流式卡片成功: cardInfo={}", cardInfo);
            
            // 5. 准备工作流输入参数
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
            inputs.put("cause_of_action", confirmedCauseOfAction);
            
            // 检查是否有知识库ID，如果有则添加到输入参数中
            if (caseInfo.getDifyKnowledgeId() != null && !caseInfo.getDifyKnowledgeId().isEmpty()) {
                log.info("案件关联知识库ID: {}", caseInfo.getDifyKnowledgeId());
                inputs.put("knowledge_id", caseInfo.getDifyKnowledgeId());
            } else {
                log.info("案件未关联知识库");
            }
            
            // 6. 创建工作流消息处理器
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
                                "正在开始生成策略分析，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        // 节点开始
                        messageService.updateStreamingContent(cardInfo, 
                                "正在基于案由生成策略分析...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        // 节点完成
                        messageService.updateStreamingContent(cardInfo, 
                                "正在整理分析结果，请稍候...", sequence[0]++);
                    } else if ("message_received".equals(chunk.getEvent())) {
                        // 收到消息块
                        if (chunk.getData() != null) {
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            if (dataObj.containsKey("message") && dataObj.getJSONObject("message").containsKey("content")) {
                                // 追加消息内容
                                String content = dataObj.getJSONObject("message").getString("content");
                                resultContent.append(content);
                                
                                // 更新流式卡片内容
                                messageService.updateStreamingContent(cardInfo, 
                                        resultContent.toString(), sequence[0]++);
                            }
                        }
                    } else if (chunk.isWorkflowFinished()) {
                        // 工作流完成
                        if (chunk.getData() != null) {
                            // 从工作流输出中提取结果
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            log.debug("工作流完成数据: {}", dataObj);
                            
                            if (dataObj.containsKey("outputs")) {
                                JSONObject outputs = dataObj.getJSONObject("outputs");
                                // 根据实际输出格式，从text字段获取结果
                                if (outputs.containsKey("text") && resultContent.length() == 0) {
                                    resultContent.append(outputs.getString("text"));
                                }
                            }
                        }
                        
                        // 获取最终结果
                        String finalContent = resultContent.length() > 0 
                                ? resultContent.toString() 
                                : "策略分析完成，但未获取到结果。";
                                
                        // 更新最终结果
                        messageService.updateStreamingContent(cardInfo, 
                                finalContent, sequence[0]++);
                        
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                        
                        // 生成Word文档并发送
                        try {
                            // 生成Word文档
                            String fileName = caseInfo.getCaseName() + "-策略分析.docx";
                            File wordFile = null;
                            try {
                                // 将Markdown转换为Word文档
                                wordFile = MarkdownToWordConverter.convertToWord(finalContent, fileName);
                                
                                if (wordFile != null && wordFile.exists()) {
                                    // 发送Word文档
                                    messageService.sendFileMessage(operatorId, wordFile.getAbsolutePath(), fileName);
                                    log.info("发送策略分析Word文档成功: {}", wordFile.getAbsolutePath());
                                    
                                    // 删除临时文件
                                    try {
                                        Files.deleteIfExists(wordFile.toPath());
                                    } catch (Exception e) {
                                        log.warn("删除临时文件失败: {}", wordFile.getAbsolutePath(), e);
                                    }
                                } else {
                                    log.error("生成Word文档失败");
                                    // 更新错误信息
                                    messageService.updateStreamingContent(cardInfo, 
                                            finalContent + "\n\nWord文档生成失败", sequence[0]++);
                                }
                            } catch (Exception e) {
                                log.error("生成或发送Word文档失败", e);
                                // 更新错误信息
                                messageService.updateStreamingContent(cardInfo, 
                                        finalContent + "\n\nWord文档生成失败: " + e.getMessage(), sequence[0]++);
                            }
                        } catch (Exception e) {
                            log.error("生成或发送Word文档失败", e);
                            messageService.updateStreamingContent(cardInfo, 
                                    finalContent + "\n\nWord文档生成失败: " + e.getMessage(), sequence[0]++);
                        }
                        
                        log.info("策略分析工作流执行完成: workflowRunId={}", chunk.getWorkflow_run_id());
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
            
            // 7. 异步执行工作流
            CompletableFuture.runAsync(() -> {
                try {
                    // 调用Dify客户端执行工作流
                    difyClient.runWorkflowStreaming(
                            inputs,         // 输入参数
                            operatorId,     // 用户ID
                            messageHandler, // 消息处理器
                            null,           // 无文件
                            "app-tuF5jDloKzk59sDgJnOjLhTl"  // 使用指定的API Key
                    );
                } catch (Exception e) {
                    log.error("执行策略分析工作流异常", e);
                    try {
                        // 更新错误信息
                        messageService.updateStreamingContent(cardInfo, 
                                "策略分析过程中发生错误: " + e.getMessage(), sequence[0]++);
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });
            
            // 8. 返回成功响应
            toast.setType("success");
            toast.setContent("策略分析请求已提交，请等待结果");
            resp.setToast(toast);
            return resp;
            
        } catch (Exception e) {
            log.error("处理策略分析确认异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    /**
     * 处理文书生成的通用方法
     * @param operatorId 操作人ID
     * @param documentType 文书类型
     * @return 卡片回调响应
     */
    @Override
    public P2CardActionTriggerResponse handleDocumentGeneration(String operatorId, String documentType) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理{}生成: operatorId={}", documentType, operatorId);
            
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
            String cardTitle = documentType + ": " + caseInfo.getCaseName();
            String cardInfo = messageService.sendStreamingMessage(operatorId, "正在处理" + documentType + "生成请求，请稍候...", cardTitle);
            
            if (cardInfo == null) {
                log.error("创建流式卡片失败");
                toast.setType("error");
                toast.setContent("创建流式卡片失败");
                resp.setToast(toast);
                return resp;
            }
            
            log.info("创建{}流式卡片成功: cardInfo={}", documentType, cardInfo);
            
            // 3. 准备工作流输入参数
            Map<String, Object> inputs = new HashMap<>();
            
            // 构建案件基本信息，确保不超过256个字符
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("案件名称: ").append(caseInfo.getCaseName());
            
            if (caseInfo.getClientName() != null && !caseInfo.getClientName().isEmpty()) {
                contentBuilder.append(", 委托人: ").append(caseInfo.getClientName());
            }
            
            // 设置工作流输入参数
            // inputs.put("type", documentType);
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
                                "正在开始" + documentType + "生成，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        // 节点开始
                        messageService.updateStreamingContent(cardInfo, 
                                "正在分析案件信息，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        // 节点完成
                        messageService.updateStreamingContent(cardInfo, 
                                "正在整理" + documentType + "内容，请稍候...", sequence[0]++);
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
                        
                        // 更新最终结果
                        String finalContent = resultContent.length() > 0 
                                ? resultContent.toString() 
                                : documentType + "生成完成，但未获取到结果。";
                                
                        messageService.updateStreamingContent(cardInfo, 
                                finalContent + "\n\n正在生成Word文档，请稍候...", sequence[0]++);
                        
                        // 生成Word文档并发送
                        try {
                            if (resultContent.length() > 0) {
                                // 生成文件名
                                String fileName = caseInfo.getCaseName() + "-" + documentType;
                                
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
                        
                        log.info("{}工作流执行完成: workflowRunId={}", documentType, chunk.getWorkflow_run_id());
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
                            "app-EOeMAYUjkIHI1bIluo8wP5MC"  // 使用指定的API Key
                    );
                } catch (Exception e) {
                    log.error("执行{}工作流异常", documentType, e);
                    try {
                        // 更新错误信息
                        messageService.updateStreamingContent(cardInfo, 
                                documentType + "生成过程中发生错误: " + e.getMessage(), sequence[0]++);
                        // 停止流式更新
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });
            
            // 6. 返回成功响应
            toast.setType("success");
            toast.setContent(documentType + "生成请求已提交，请等待结果");
            resp.setToast(toast);
            return resp;
            
        } catch (Exception e) {
            log.error("处理{}生成异常", documentType, e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
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
                            "app-6QQh84PayZnD2GDL1lfVtNNs"  // 使用指定的API Key
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