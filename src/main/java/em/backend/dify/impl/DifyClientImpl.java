package em.backend.dify.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import em.backend.dify.IDifyClient;
import em.backend.dify.model.ChatCompletionResponse;
import em.backend.dify.model.ChunkResponse;
import em.backend.dify.model.FileObject;
import em.backend.mapper.UserGroupMapper;
import em.backend.pojo.UserGroup;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.util.FileCopyUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;

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
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            Boolean autoGenerateName) {
        log.info("调用Dify流式API: query=[{}], user=[{}]", query, user);

        executorService.submit(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            String newConversationId = conversationId;
            try {
                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("query", query);
                requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
                requestBody.put("response_mode", "streaming");
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
                
                
                // 创建连接
                URL url = new URL(apiUrl + "/chat-messages");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                
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
                        String jsonData = line.substring(6); // 去掉 "data: " 前缀
                        ChunkResponse chunk = JSON.parseObject(jsonData, ChunkResponse.class);
                        messageHandler.accept(chunk);
//                        System.out.println(chunk);
                        // 捕获新的会话ID
                        if ( newConversationId == null || newConversationId.isEmpty()) {
                            newConversationId = chunk.getConversation_id();
                            log.info("获取到新会话ID: {}", newConversationId);
                            // 保存到数据库
                            saveConversationId(user, newConversationId);
                        }
//                        System.out.println("line:"+line);
                        // 如果是结束事件或错误事件，则退出循环
                        if ("message_end".equals(chunk.getEvent()) || "error".equals(chunk.getEvent())) {
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
            UserGroup userGroup = new UserGroup();
            userGroup.setOpenId(userId);
            userGroup.setChatId(conversationId);
            userGroupMapper.insert(userGroup);
            log.info("成功保存会话ID: userId={}, conversationId={}", userId, conversationId);
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
} 