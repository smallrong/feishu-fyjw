package em.backend.dify.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import em.backend.dify.IDifyClient;
import em.backend.dify.model.ChatCompletionResponse;
import em.backend.dify.model.ChunkResponse;
import em.backend.dify.model.FileObject;
import em.backend.dify.model.workflow.WorkflowCompletionResponse;
import em.backend.dify.model.workflow.WorkflowChunkResponse;
import em.backend.dify.model.workflow.WorkflowRunRequest;
import em.backend.mapper.UserGroupMapper;
import em.backend.mapper.LegalResearchMessageMapper;
import em.backend.mapper.UserStatusMapper;
import em.backend.mapper.LegalResearchGroupMapper;
import em.backend.pojo.UserGroup;
import em.backend.pojo.LegalResearchMessage;
import em.backend.pojo.UserStatus;
import em.backend.pojo.LegalResearchGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyClientImpl implements IDifyClient {

    @Value("${dify.api-url:https://api.dify.ai/v1}")
    private String apiUrl;
    
    @Value("${dify.api-key}")
    private String apiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final UserGroupMapper userGroupMapper;
    private final LegalResearchMessageMapper legalResearchMessageMapper;
    private final UserStatusMapper userStatusMapper;
    private final LegalResearchGroupMapper legalResearchGroupMapper;
    @PostConstruct
    public void init() {
        log.info("Dify API URL: {}", apiUrl);
        log.info("Dify API Key 已加载: {}", apiKey != null && !apiKey.isEmpty() ? "是" : "否");
        
        if (apiKey == null || apiKey.isEmpty() || "your-api-key-here".equals(apiKey)) {
            log.warn("Dify API Key 未设置或使用了默认值，API调用可能会失败");
        }
    }

    @Override
    public ChatCompletionResponse chatBlocking(
            String query, 
            Map<String, Object> inputs, 
            String user, 
            String conversationId, 
            List<FileObject> files, 
            Boolean autoGenerateName) {
        
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
            requestBody.put("response_mode", "blocking");
            requestBody.put("user", user);
            
            if (conversationId != null) {
                requestBody.put("conversation_id", conversationId);
            }
            
            if (files != null && !files.isEmpty()) {
                requestBody.put("files", files);
            }
            
            if (autoGenerateName != null) {
                requestBody.put("auto_generate_name", autoGenerateName);
            }
            
            // 输出请求信息
            log.info("Dify API 请求URL: {}", apiUrl + "/chat-messages");
            log.info("Dify API 请求体: {}", JSON.toJSONString(requestBody));
            log.info("Dify API 请求头: Content-Type=application/json, Authorization=Bearer ****" + 
                    (apiKey != null && apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : ""));
            
            // 发送请求
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/chat-messages",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            
            // 输出响应信息
            log.info("Dify API 响应状态码: {}", response.getStatusCodeValue());
            log.info("Dify API 响应体: {}", response.getBody());
            
            // 解析响应
            return JSON.parseObject(response.getBody(), ChatCompletionResponse.class);
        } catch (Exception e) {
            log.error("Dify API调用失败", e);
            throw new RuntimeException("Dify API调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStreaming(
            String query, 
            Map<String, Object> inputs, 
            String user, 
            Consumer<ChunkResponse> messageHandler,
            String conversationId, 
            List<FileObject> files, 
            Boolean autoGenerateName,
            String _apiKey) {
        log.info("调用Dify流式API: query=[{}], user=[{}]", query, user);

        executorService.submit(() -> {
            HttpURLConnection connection = null;

            BufferedReader reader = null;
            String newConversationId = conversationId;

            if("load".equals(newConversationId)){
                newConversationId = null;
            }
            // 添加StringBuilder用于收集AI回复
            final StringBuilder aiResponse = new StringBuilder();
            
            try {
                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("query", query);
                requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
                requestBody.put("response_mode", "streaming");
                requestBody.put("user", user);

                if (newConversationId != null) {
                    requestBody.put("conversation_id", newConversationId);
                }
                
                if (files != null && !files.isEmpty()) {
                    requestBody.put("files", files);
                }
                
                if (autoGenerateName != null) {
                    requestBody.put("auto_generate_name", autoGenerateName);
                }
                
                
                // 创建连接
                URL url = new URL(apiUrl + "/chat-messages");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + (_apiKey == null? apiKey:_apiKey) );
                
                // 添加必要的请求头，模拟浏览器行为  不知道为什么不加报错403
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setDoOutput(true);
                
                // 写入请求体
                String requestBodyJson = JSON.toJSONString(requestBody);
                connection.getOutputStream().write(requestBodyJson.getBytes("UTF-8"));
                connection.getOutputStream().flush();
                
                // 读取响应
                int responseCode = connection.getResponseCode();
                
                if (responseCode >= 400) {
                    // 读取错误流
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    log.error("Dify API 请求失败: {} - {}", responseCode, errorResponse.toString());
                    
                    ChunkResponse errorChunk = new ChunkResponse();
                    errorChunk.setEvent("error");
                    errorChunk.setMessage("Dify API调用失败，状态码: " + responseCode);
                    messageHandler.accept(errorChunk);
                    return;
                }
                
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6);
                        ChunkResponse chunk = JSON.parseObject(jsonData, ChunkResponse.class);
                        
                        // 收集AI的回复
                        if ("message".equals(chunk.getEvent()) && chunk.getAnswer() != null) {
                            aiResponse.append(chunk.getAnswer());
                        }
                        
                        messageHandler.accept(chunk);

                        if ( conversationId == null && _apiKey == null ) {
                            // 只有当原来没有对话组的时候，并且不在法律研究状态才插入
                            newConversationId = chunk.getConversation_id();
                            log.info("获取到新会话ID: {}", newConversationId);
                            saveConversationId(user, newConversationId);
                        }

                        if ("message_end".equals(chunk.getEvent()) || "error".equals(chunk.getEvent())) {
                            // 如果是法律研究对话，保存消息记录
                            if (_apiKey != null) {
                                try {
                                    if(newConversationId == null || newConversationId.isEmpty()){
                                        newConversationId = chunk.getConversation_id();
                                        log.info("获取到法律研究对话ID: {}", newConversationId);
                                    }
                                    
                                    // 保存消息记录
                                    LegalResearchMessage message = new LegalResearchMessage();
                                    message.setGroupId(newConversationId);
                                    message.setUserMessage(query);
                                    message.setAssistantMessage(aiResponse.toString());
                                    legalResearchMessageMapper.insert(message);
                                    log.info("保存法律研究对话记录成功: groupId={}, conversationId={}",
                                            newConversationId, newConversationId);

                                    // 获取用户当前的 case_id
                                    UserStatus currentStatus = userStatusMapper.selectOne(
                                        new QueryWrapper<UserStatus>().eq("open_id", user));
                                    if (currentStatus == null || currentStatus.getCurrentCaseId() == null) {
                                        log.error("未找到用户当前案件信息: user={}", user);
                                        return;
                                    }

                                    LegalResearchGroup group = new LegalResearchGroup();
                                    log.debug("原来的id为{}",conversationId);
                                    if(conversationId == null || conversationId.isEmpty()) {
                                     //只有当原来没有对话组的时候，我才插入一个新的对话组
                                        group.setDifyGroupId(newConversationId);
                                        group.setOpenId(user);
                                        group.setCaseId(currentStatus.getCurrentCaseId());
                                        legalResearchGroupMapper.insert(group);
                                        log.info("保存法律研究组信息成功: difyGroupId={}, openId={}, caseId={}",
                                                newConversationId, user, currentStatus.getCurrentCaseId());
                                    }



                                    // 更新用户状态中的法律研究组ID
                                    UserStatus userStatus = new UserStatus();
                                    userStatus.setCurrentLegalResearchGroupId(newConversationId);
                                    userStatus.setLegal(true);
                                    userStatusMapper.update(userStatus, 
                                        new QueryWrapper<UserStatus>().eq("open_id", user));
                                    log.info("更新用户法律研究组ID成功: user={}, groupId={}", user, newConversationId);
                                } catch (Exception e) {
                                    log.error("保存法律研究对话记录失败", e);
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Dify 流式API调用失败", e);
                ChunkResponse errorChunk = new ChunkResponse();
                errorChunk.setEvent("error");
                errorChunk.setMessage("Dify API调用失败: " + e.getMessage());
                messageHandler.accept(errorChunk);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (IOException e) {
                    log.error("关闭连接失败", e);
                }
            }
            
            log.info("Dify流式API调用完成");
        });
    }

    private void saveConversationId(String userId, String conversationId) {
        try {
            UserGroup userGroup = userGroupMapper.selectById(userId);
            if(userGroup == null || userGroup.getChatId() == null) {
                UserGroup group = new UserGroup();
                group.setOpenId(userId);
                group.setChatId(conversationId);
                userGroupMapper.insert(group);
                log.info("成功保存会话ID: userId={}, conversationId={}", userId, conversationId);
            }
           } catch (Exception e) {
            log.error("保存会话ID失败", e);
        }
    }

    @Override
    public boolean stopResponse(String taskId) {
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("task_id", taskId);
            
            // 输出请求信息
            log.info("Dify API 停止响应请求URL: {}", apiUrl + "/chat-messages/stop-answer");
            log.info("Dify API 停止响应请求体: {}", JSON.toJSONString(requestBody));
            log.info("Dify API 停止响应请求头: Content-Type=application/json, Authorization=Bearer ****" + 
                    (apiKey != null && apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : ""));
            
            // 发送请求
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/chat-messages/stop-answer",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            
            // 输出响应信息
            log.info("Dify API 停止响应状态码: {}", response.getStatusCodeValue());
            log.info("Dify API 停止响应体: {}", response.getBody());
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("停止Dify响应失败", e);
            return false;
        }
    }

    @Override
    public FileObject uploadFile(MultipartFile file, String user) {
        try {
            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !(
                contentType.equals("image/png") || 
                contentType.equals("image/jpeg") || 
                contentType.equals("image/jpg") || 
                contentType.equals("image/webp") || 
                contentType.equals("image/gif"))) {
                throw new IllegalArgumentException("不支持的文件类型，仅支持 png, jpg, jpeg, webp, gif 格式");
            }
            
            // 创建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 使用匿名内部类扩展ByteArrayResource，但不覆盖不存在的方法
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            
            // 添加文件和用户信息
            body.add("file", fileResource);
            body.add("user", user);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // 添加浏览器模拟头，避免Cloudflare拦截
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            
            // 创建请求实体
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl + "/files/upload",
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(response.getBody());
            
            // 创建并返回FileObject
            FileObject fileObject = new FileObject();
            fileObject.setType("image"); // 目前仅支持图片
            fileObject.setTransfer_method("local_file");
            fileObject.setUpload_file_id(jsonResponse.getString("id"));
            
            return fileObject;
        } catch (Exception e) {
            log.error("Dify 文件上传失败", e);
            throw new RuntimeException("Dify 文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public FileObject uploadFile(File file, String user) {
        try {
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file.toPath());
            
            // 获取文件类型
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                // 根据扩展名猜测内容类型
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".png")) contentType = "image/png";
                else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
                else if (fileName.endsWith(".webp")) contentType = "image/webp";
                else if (fileName.endsWith(".gif")) contentType = "image/gif";
                else throw new IllegalArgumentException("不支持的文件类型，仅支持 png, jpg, jpeg, webp, gif 格式");
            }
            
            // 创建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 使用匿名内部类扩展ByteArrayResource，但不覆盖不存在的方法
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return file.getName();
                }
            };
            
            // 添加文件和用户信息
            body.add("file", fileResource);
            body.add("user", user);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // 添加浏览器模拟头，避免Cloudflare拦截
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            
            // 创建请求实体
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl + "/files/upload",
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(response.getBody());
            
            // 创建并返回FileObject
            FileObject fileObject = new FileObject();
            fileObject.setType("image"); // 目前仅支持图片
            fileObject.setTransfer_method("local_file");
            fileObject.setUpload_file_id(jsonResponse.getString("id"));
            
            return fileObject;
        } catch (Exception e) {
            log.error("Dify 文件上传失败", e);
            throw new RuntimeException("Dify 文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public WorkflowCompletionResponse runWorkflowBlocking(
            Map<String, Object> inputs, String user, List<FileObject> files, String apiKey) {
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }
        
        log.info("【Dify客户端】开始执行工作流（阻塞模式）: user={}", user);
        log.debug("【Dify客户端】工作流输入参数: inputs={}, files={}", inputs, files);
        
        try {
            // 创建请求体
            WorkflowRunRequest request = WorkflowRunRequest.createBlockingRequest(inputs, user, files);
            
            // 创建HTTP头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            
            // 添加必要的请求头，模拟浏览器行为，避免Cloudflare拦截
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            
            // 创建HTTP实体
            HttpEntity<WorkflowRunRequest> entity = new HttpEntity<>(request, headers);
            
            log.debug("【Dify客户端】发送工作流请求（阻塞模式）: url={}", apiUrl + "/workflows/run");
            log.debug("【Dify客户端】请求头: {}", headers);
            log.debug("【Dify客户端】请求体: {}", JSON.toJSONString(request));
            
            // 发送请求
            ResponseEntity<WorkflowCompletionResponse> response = restTemplate.exchange(
                    apiUrl + "/workflows/run",
                    HttpMethod.POST,
                    entity,
                    WorkflowCompletionResponse.class);
            
            WorkflowCompletionResponse result = response.getBody();
            
            log.info("【Dify客户端】工作流执行完成（阻塞模式）: workflowRunId={}, taskId={}, status={}", 
                    result.getWorkflow_run_id(), result.getTask_id(), 
                    result.getData() != null ? result.getData().getStatus() : "unknown");
            
            return result;
        } catch (Exception e) {
            log.error("【Dify客户端】工作流执行异常（阻塞模式）", e);
            throw e;
        }
    }
    
    @Override
    public void runWorkflowStreaming(
            Map<String, Object> inputs, String user, 
            Consumer<WorkflowChunkResponse> messageHandler, List<FileObject> files,
            String apiKey) {
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }
        
        log.info("【Dify客户端】开始执行工作流（流式模式）: user={}", user);
        log.debug("【Dify客户端】工作流输入参数: inputs={}, files={}", inputs, files);
        
        executorService.submit(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                // 创建请求体
                WorkflowRunRequest request = WorkflowRunRequest.createStreamingRequest(inputs, user, files);
                
                // 创建连接
                URL url = new URL(apiUrl + "/workflows/run");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                
                // 添加必要的请求头，模拟浏览器行为
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setDoOutput(true);
                
                log.debug("【Dify客户端】发送工作流请求（流式模式）: url={}", apiUrl + "/workflows/run");
                log.debug("【Dify客户端】请求体: {}", JSON.toJSONString(request));
                
                // 写入请求体
                String requestBodyJson = JSON.toJSONString(request);
                connection.getOutputStream().write(requestBodyJson.getBytes("UTF-8"));
                connection.getOutputStream().flush();
                
                // 读取响应
                int responseCode = connection.getResponseCode();
                
                if (responseCode >= 400) {
                    // 读取错误流
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    log.error("【Dify客户端】工作流请求失败: {} - {}", responseCode, errorResponse.toString());
                    
                    WorkflowChunkResponse errorChunk = new WorkflowChunkResponse();
                    errorChunk.setEvent("error");
                    // 设置错误信息
                    messageHandler.accept(errorChunk);
                    return;
                }
                
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6); // 去掉 "data: " 前缀
                        WorkflowChunkResponse chunk = JSON.parseObject(jsonData, WorkflowChunkResponse.class);
                        
                        log.debug("【Dify客户端】收到工作流事件: event={}, taskId={}", 
                                chunk.getEvent(), chunk.getTask_id());
                        
                        messageHandler.accept(chunk);
                        
                        // 如果是工作流完成事件，记录日志并退出循环
                        if (chunk.isWorkflowFinished()) {
                            log.info("【Dify客户端】工作流执行完成（流式模式）: workflowRunId={}, taskId={}", 
                                    chunk.getWorkflow_run_id(), chunk.getTask_id());
                            break;
                        }
                    }
                }
                
                log.info("【Dify客户端】工作流流式响应完成");
            } catch (Exception e) {
                log.error("【Dify客户端】工作流执行异常（流式模式）", e);
                WorkflowChunkResponse errorChunk = new WorkflowChunkResponse();
                errorChunk.setEvent("error");
                // 设置错误信息
                messageHandler.accept(errorChunk);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (IOException e) {
                    log.error("【Dify客户端】关闭连接失败", e);
                }
            }
        });
    }
    
    @Override
    public boolean stopWorkflow(String taskId, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }
        
        log.info("【Dify客户端】停止工作流执行: taskId={}", taskId);
        
        try {
            // 创建HTTP头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            
            // 添加必要的请求头，模拟浏览器行为，避免Cloudflare拦截
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            
            // 创建HTTP实体
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            log.debug("【Dify客户端】发送停止工作流请求: url={}", apiUrl + "/workflows/stop?task_id=" + taskId);
            
            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/workflows/stop?task_id=" + taskId,
                    HttpMethod.POST,
                    entity,
                    Map.class);
            
            boolean result = response.getStatusCode().is2xxSuccessful();
            
            log.info("【Dify客户端】工作流停止结果: taskId={}, result={}", taskId, result);
            
            return result;
        } catch (Exception e) {
            log.error("【Dify客户端】停止工作流异常: taskId={}", taskId, e);
            return false;
        }
    }
} 