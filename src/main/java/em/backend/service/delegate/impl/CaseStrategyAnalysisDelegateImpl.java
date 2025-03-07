package em.backend.service.delegate.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import em.backend.dify.IDifyClient;
import em.backend.dify.model.workflow.WorkflowChunkResponse;
import em.backend.mapper.CaseInfoMapper;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICardTemplateService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import em.backend.service.delegate.ICaseStrategyAnalysisDelegate;
import em.backend.util.MarkdownToWordConverter;
import em.backend.common.CardTemplateConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 案件策略分析委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseStrategyAnalysisDelegateImpl implements ICaseStrategyAnalysisDelegate {

    private static final String CAUSE_ANALYSIS_WORKFLOW_KEY = "app-CuItMOSRz9WGVoQCGyeEDbKC";
    private static final String STRATEGY_ANALYSIS_WORKFLOW_KEY = "app-CuItMOSRz9WGVoQCGyeEDbKC";

    private final CaseInfoMapper caseInfoMapper;
    private final IUserStatusService userStatusService;
    private final ICardTemplateService cardTemplateService;
    private final IMessageService messageService;
    private final IDifyClient difyClient;

    /**
     * 获取当前案件状态
     */
    private UserStatus getCurrentCase(String openId) {
        return userStatusService.lambdaQuery()
                .eq(UserStatus::getOpenId, openId)
                .one();
    }

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
            
            CaseInfo caseInfo = caseInfoMapper.selectById(userStatus.getCurrentCaseId());
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
                            
                            // 使用模板ID: CONFIRM_CAUSE
                            String cardContent = cardTemplateService.buildTemplateCard(CardTemplateConstants.CONFIRM_CAUSE, cardParams);
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
                            CAUSE_ANALYSIS_WORKFLOW_KEY  // 使用指定的API Key
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

    @Override
    public P2CardActionTriggerResponse handleStrategyAnalysisConfirm(Map<String, Object> formData, String operatorId, String cardId) {
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
            
            CaseInfo caseInfo = caseInfoMapper.selectById(userStatus.getCurrentCaseId());
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
                
                // 使用模板ID: CONFIRM_CAUSE_SUCCESS
                String cardContent = cardTemplateService.buildTemplateCard(CardTemplateConstants.CONFIRM_CAUSE_SUCCESS, cardParams);
                
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
                            STRATEGY_ANALYSIS_WORKFLOW_KEY  // 使用指定的API Key
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
} 