package em.backend.service.delegate.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
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
import em.backend.service.delegate.ICaseLegalResearchDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 案件法律研究委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseLegalResearchDelegateImpl implements ICaseLegalResearchDelegate {

    private static final String LEGAL_RESEARCH_WORKFLOW_KEY = "app-CuItMOSRz9WGVoQCGyeEDbKC";

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
    public P2CardActionTriggerResponse handleLegalResearchInput(Map<String, Object> formData, String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理法律研究输入: formData={}, operatorId={}", formData, operatorId);

            // 1. 获取用户输入的研究内容

            // 2. 获取当前案件信息
            UserStatus userStatus = getCurrentCase(operatorId);
            if (userStatus == null || userStatus.getCurrentCaseId() == null) {
                log.error("未找到当前案件");
                toast.setType("error");
                toast.setContent("未找到当前案件");
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
            String researchQuery = "\n案件名称: " + caseInfo.getCaseName();
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
                            LEGAL_RESEARCH_WORKFLOW_KEY  // 使用指定的API Key
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

    @Override
    public void sendLegalResearchCard(String openId, String caseId) {
        try {
            // 查询案件信息
            CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
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
} 