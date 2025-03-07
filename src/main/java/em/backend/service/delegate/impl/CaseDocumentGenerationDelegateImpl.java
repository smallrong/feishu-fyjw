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
import em.backend.service.IFeishuFolderService;
import em.backend.service.delegate.ICaseDocumentGenerationDelegate;
import em.backend.util.MarkdownToWordConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 案件文书生成委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseDocumentGenerationDelegateImpl implements ICaseDocumentGenerationDelegate {

    private static final String DOCUMENT_GENERATION_WORKFLOW_KEY = "app-EOeMAYUjkIHI1bIluo8wP5MC";

    private final CaseInfoMapper caseInfoMapper;
    private final IUserStatusService userStatusService;
    private final ICardTemplateService cardTemplateService;
    private final IMessageService messageService;
    private final IDifyClient difyClient;
    private final IFeishuFolderService folderService;

    /**
     * 获取当前案件状态
     */
    private UserStatus getCurrentCase(String openId) {
        return userStatusService.lambdaQuery()
                .eq(UserStatus::getOpenId, openId)
                .one();
    }

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
            
            CaseInfo caseInfo = caseInfoMapper.selectById(userStatus.getCurrentCaseId());
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
            inputs.put("type", documentType);
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
                            DOCUMENT_GENERATION_WORKFLOW_KEY  // 使用指定的API Key
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
}