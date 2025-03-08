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
import em.backend.pojo.LegalResearchGroup;
import em.backend.pojo.UserStatus;
import em.backend.service.ICardTemplateService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import em.backend.service.delegate.ICaseLegalResearchDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import em.backend.common.CardTemplateConstants;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import em.backend.mapper.LegalResearchGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import em.backend.mapper.LegalResearchMessageMapper;
import em.backend.pojo.LegalResearchMessage;
import em.backend.service.impl.CaseServiceImpl;

/**
 * 案件法律研究委托实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseLegalResearchDelegateImpl extends ServiceImpl<LegalResearchGroupMapper, LegalResearchGroup> implements ICaseLegalResearchDelegate {

    private static final String LEGAL_RESEARCH_WORKFLOW_KEY = "app-CuItMOSRz9WGVoQCGyeEDbKC";
    private static final String LEGAL_RESEARCH_CANCEL_WORKFLOW_KEY = "app-vau98ZMo2hxg6kBMq83eAKIW";

    private final CaseInfoMapper caseInfoMapper;
    private final IUserStatusService userStatusService;
    private final ICardTemplateService cardTemplateService;
    private final IMessageService messageService;
    private final IDifyClient difyClient;
    private final LegalResearchMessageMapper legalResearchMessageMapper;

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
            String studyInput = String.valueOf(formData.get("input_keyword"));

            // log.info("用户输入的法律研究内容: {}", studyInput)

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
            String researchQuery =  studyInput; 
            // + "\n案件名称: " + caseInfo.getCaseName();
            // if (caseInfo.getCaseDesc() != null && !caseInfo.getCaseDesc().isEmpty()) {
            //     researchQuery += "\n案件描述: " + caseInfo.getCaseDesc();
            // }
            
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
                                if (outputs.containsKey("text")) {
                                    String text = outputs.getString("text");
                                    log.info("工作流返回文本内容: {}", text);
                                    resultContent.append(text);
                                } else {
                                    log.warn("工作流输出中未找到text字段: outputs={}", outputs);
                                }
                            } else {
                                log.warn("工作流输出中未找到outputs字段: data={}", dataObj);
                            }
                        } else {
                            log.warn("工作流完成但未返回数据");
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

    @Override
    public void handleLegalResearchMenuEvent(String openId) {
        try {
            log.info("处理法律研究菜单事件: openId={}", openId);
            
            // 获取当前案件状态
            UserStatus userStatus = getCurrentCase(openId);
            if (userStatus == null || userStatus.getCurrentCaseId() == null) {
                log.warn("未找到当前案件: openId={}", openId);
                messageService.sendMessage(openId, "请先选择一个案件", openId);
                return;
            }

            // 获取案件信息
            CaseInfo caseInfo = caseInfoMapper.selectById(userStatus.getCurrentCaseId());
            if (caseInfo == null) {
                log.error("案件不存在: caseId={}", userStatus.getCurrentCaseId());
                messageService.sendMessage(openId, "当前案件不存在", openId);
                return;
            }

            // 构建并发送法律研究卡片
            Map<String, Object> params = new HashMap<>();
            params.put("case", caseInfo.getCaseName());
            
            String cardContent = cardTemplateService.buildTemplateCard(CardTemplateConstants.LEGAL_RESEARCH, params);
            messageService.sendCardMessage(openId, cardContent);
            
            log.info("法律研究卡片发送成功: openId={}, caseId={}", openId, caseInfo.getId());
        } catch (Exception e) {
            log.error("处理法律研究菜单事件异常: openId={}", openId, e);
            messageService.sendMessage(openId, "系统处理异常，请稍后重试", openId);
        }
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchCancel(Map<String, Object> formData, String operatorId,String context) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理法律研究取消事件: formData={}, operatorId={}", formData, operatorId);

            // 1. 获取当前案件信息
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

            // 2. 创建并发送流式卡片
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

            // 3. 准备工作流输入参数
            Map<String, Object> inputs = new HashMap<>();
            
            // 构建案件基本信息
            StringBuilder caseInfoBuilder = new StringBuilder();
            caseInfoBuilder.append("案件名称: ").append(caseInfo.getCaseName());
            
            if (caseInfo.getClientName() != null && !caseInfo.getClientName().isEmpty()) {
                caseInfoBuilder.append("\n委托人: ").append(caseInfo.getClientName());
            }
            
            if (caseInfo.getCaseDesc() != null && !caseInfo.getCaseDesc().isEmpty()) {
                caseInfoBuilder.append("\n案件描述: ").append(caseInfo.getCaseDesc());
            }
            
            inputs.put("case", caseInfoBuilder.toString());
            inputs.put("context",context);
            // 添加知识库ID
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
                        messageService.updateStreamingContent(cardInfo,
                                "正在开始法律研究分析，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeStarted()) {
                        messageService.updateStreamingContent(cardInfo,
                                "正在分析案件信息，请稍候...", sequence[0]++);
                    } else if (chunk.isNodeFinished()) {
                        messageService.updateStreamingContent(cardInfo,
                                "正在整理分析结果，请稍候...", sequence[0]++);
                    } else if (chunk.isWorkflowFinished()) {
                        if (chunk.getData() != null) {
                            JSONObject dataObj = JSONObject.parseObject(JSON.toJSONString(chunk.getData()));
                            log.debug("工作流完成数据: {}", dataObj);

                            if (dataObj.containsKey("outputs")) {
                                JSONObject outputs = dataObj.getJSONObject("outputs");
                                if (outputs.containsKey("text")) {
                                    String text = outputs.getString("text");
                                    log.info("工作流返回文本内容: {}", text);
                                    resultContent.append(text);
                                } else {
                                    log.warn("工作流输出中未找到text字段: outputs={}", outputs);
                                }
                            } else {
                                log.warn("工作流输出中未找到outputs字段: data={}", dataObj);
                            }
                        } else {
                            log.warn("工作流完成但未返回数据");
                        }

                        String finalContent = resultContent.length() > 0
                                ? resultContent.toString()
                                : "法律研究分析完成，但未获取到结果。";

                        log.info("最终处理结果: {}", finalContent);

                        // 更新流式消息的最终内容
                        messageService.updateStreamingContent(cardInfo, finalContent, sequence[0]++);
                        messageService.stopStreamingMode(cardInfo, sequence[0]);

                        // 发送确认卡片
                        try {
                            Map<String, Object> cardParams = new HashMap<>();
                            cardParams.put("case", caseInfo.getCaseName());
                            cardParams.put("content", finalContent);
                            
                            log.debug("准备发送确认卡片，参数: case={}, content长度={}", 
                                    caseInfo.getCaseName(), finalContent.length());
                            
                            String confirmCardContent = cardTemplateService.buildTemplateCard(
                                    CardTemplateConstants.LEGAL_RESEARCH_CONFIRM, 
                                    cardParams
                            );
                            
                            log.debug("生成的卡片内容: {}", confirmCardContent);
                            
                            messageService.sendCardMessage(operatorId, confirmCardContent);
                            log.info("发送法律研究确认卡片成功: operatorId={}", operatorId);
                        } catch (Exception e) {
                            log.error("发送法律研究确认卡片失败: operatorId={}, error={}", operatorId, e.getMessage(), e);
                        }

                        log.info("法律研究工作流执行完成: workflowRunId={}", chunk.getWorkflow_run_id());
                    }
                } catch (Exception e) {
                    log.error("处理工作流消息异常", e);
                }
            };

            // 5. 异步执行工作流
            CompletableFuture.runAsync(() -> {
                try {
                    difyClient.runWorkflowStreaming(
                            inputs,
                            operatorId,
                            messageHandler,
                            null,
                            LEGAL_RESEARCH_CANCEL_WORKFLOW_KEY
                    );
                } catch (Exception e) {
                    log.error("执行法律研究工作流异常", e);
                    try {
                        messageService.updateStreamingContent(cardInfo,
                                "法律研究分析过程中发生错误: " + e.getMessage(), sequence[0]++);
                        messageService.stopStreamingMode(cardInfo, sequence[0]);
                    } catch (Exception ex) {
                        log.error("更新错误信息失败", ex);
                    }
                }
            });

            // 6. 返回成功响应
            toast.setType("success");
            toast.setContent("法律研究分析请求已提交，请等待结果");
            resp.setToast(toast);
            
            // 7. 更新用户状态，退出法律研究模式
            try {
                UserStatus currentUserStatus = userStatusService.lambdaQuery()
                        .eq(UserStatus::getOpenId, operatorId)
                        .one();
                if (currentUserStatus != null) {
                    currentUserStatus.setCurrentLegalResearchGroupId(null);
                    currentUserStatus.setLegal(false);
                    userStatusService.updateById(currentUserStatus);
                    log.info("已清除用户法律研究状态: operatorId={}", operatorId);
                }
            } catch (Exception e) {
                log.error("更新用户状态失败，但不影响主流程", e);
            }
            
            return resp;

        } catch (Exception e) {
            log.error("处理法律研究取消事件异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchConfirm(String operatorId) {
        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();

        try {
            log.info("处理法律研究确认事件: operatorId={}", operatorId);

            // 1. 获取当前案件信息
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

            // 2. 创建新的法律研究对话组
//            LegalResearchGroup group = new LegalResearchGroup();
//            group.setCaseId(caseInfo.getId());
//            group.setOpenId(operatorId);
//            save(group);

            log.info("创建法律研究对话组成功: groupId={}");

            // 3. 更新用户状态
            userStatus.setLegal(true);
            userStatusService.updateById(userStatus);

            log.info("更新用户状态成功: openId={}, groupId={}", operatorId);

            // 4. 发送提示消息
            messageService.sendMessage(operatorId, 
                "已进入法律研究对话模式，请在对话框中输入您想研究的内容。", operatorId);

            // 5. 返回成功响应
            toast.setType("success");
            toast.setContent("已进入法律研究对话模式");
            resp.setToast(toast);
            return resp;

        } catch (Exception e) {
            log.error("处理法律研究确认事件异常", e);
            toast.setType("error");
            toast.setContent("系统处理失败");
            resp.setToast(toast);
            return resp;
        }
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchWithContext(Map<String, Object> formData, String operatorId) {
        log.info("处理基于历史对话上下文的法律研究请求: operatorId={}", operatorId);
        
        try {
            // 查询用户当前法律研究组ID
            UserStatus userStatus = userStatusService.lambdaQuery()
                    .eq(UserStatus::getOpenId, operatorId)
                    .one();
            
            if (userStatus == null || userStatus.getCurrentLegalResearchGroupId() == null) {
                log.warn("用户没有当前法律研究会话: {}", operatorId);
                return handleLegalResearchCancel(formData, operatorId, null);
            }
            
            // 查询历史消息
            List<LegalResearchMessage> messages = legalResearchMessageMapper.selectList(
                    new LambdaQueryWrapper<LegalResearchMessage>()
                    .eq(LegalResearchMessage::getGroupId, userStatus.getCurrentLegalResearchGroupId())
                    .orderByAsc(LegalResearchMessage::getCreateTime)); // 按时间正序，越早的消息越在前面
            
            if (messages.isEmpty()) {
                log.warn("用户法律研究会话没有历史消息: groupId={}", userStatus.getCurrentLegalResearchGroupId());
                return handleLegalResearchCancel(formData, operatorId, null);
            }
            
            // 拼接上下文内容
            StringBuilder context = new StringBuilder();
            for (LegalResearchMessage message : messages) {
                context.append("用户: ").append(message.getUserMessage()).append("\n");
                context.append("助手: ").append(message.getAssistantMessage()).append("\n\n");
            }
            
            log.info("获取到法律研究历史消息，数量: {}", messages.size());
            
            // 更新用户状态，清除法律研究状态
            userStatus.setCurrentLegalResearchGroupId(null);
            userStatus.setLegal(false);
            userStatusService.updateById(userStatus);
            log.info("已更新用户状态，清除法律研究状态: operatorId={}", operatorId);
            
            return handleLegalResearchCancel(formData, operatorId, context.toString());
            
        } catch (Exception e) {
            log.error("处理基于历史对话上下文的法律研究请求异常", e);
            P2CardActionTriggerResponse response = buildErrorResponse("获取历史对话失败，请重试");
            return response;
        }
    }

    @Override
    public P2CardActionTriggerResponse handleLegalResearchLogout(String operatorId) {
        log.info("处理法律研究退出事件: operatorId={}", operatorId);
        
        try {
            // 查询用户当前状态
            UserStatus userStatus = userStatusService.lambdaQuery()
                    .eq(UserStatus::getOpenId, operatorId)
                    .one();
            
            if (userStatus != null) {
                // 清除法律研究状态
                userStatus.setCurrentLegalResearchGroupId(null);
                userStatus.setLegal(false);
                userStatusService.updateById(userStatus);
                log.info("已清除用户法律研究状态: operatorId={}", operatorId);
            } else {
                log.warn("未找到用户状态: operatorId={}", operatorId);
            }
            
            // 返回带有Toast的响应
            P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
            CallBackToast toast = new CallBackToast();
            toast.setType("success");
            toast.setContent("已退出法律研究模式");
            response.setToast(toast);
            return response;
            
        } catch (Exception e) {
            log.error("处理法律研究退出事件异常", e);
            return buildErrorResponse("退出法律研究模式失败，请重试");
        }
    }
    
    /**
     * 构建错误响应
     */
    private P2CardActionTriggerResponse buildErrorResponse(String message) {
        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        toast.setType("error");
        toast.setContent(message);
        response.setToast(toast);
        return response;
    }
} 