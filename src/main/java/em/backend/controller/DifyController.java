package em.backend.controller;

import em.backend.dify.IDifyClient;
import em.backend.dify.model.ChatCompletionResponse;
import em.backend.dify.model.ChunkResponse;
import em.backend.dify.model.FileObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {

    private final IDifyClient difyClient;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();


    // 测试接口
    /**
     * 阻塞模式聊天接口
     * 
     * @param query 用户输入/提问内容
     * @param user 用户标识
     * @return 聊天完成响应
     */
    @PostMapping("/chat")
    public ChatCompletionResponse chat(
            @RequestParam String query,
            @RequestParam String user,
            @RequestParam(required = false) String conversationId) {
        
        return difyClient.chatBlocking(query, null, user, conversationId, null, null);
    }

    /**
     * 阻塞模式聊天接口 - 高级版
     * 
     * @param requestBody 请求体，包含所有参数
     * @return 聊天完成响应
     */
    @PostMapping("/chat/advanced")
    public ChatCompletionResponse chatAdvanced(@RequestBody Map<String, Object> requestBody) {
        String query = (String) requestBody.get("query");
        String user = (String) requestBody.get("user");
        String conversationId = (String) requestBody.get("conversation_id");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) requestBody.get("inputs");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesMap = (List<Map<String, Object>>) requestBody.get("files");
        
        List<FileObject> files = null;
        if (filesMap != null && !filesMap.isEmpty()) {
            files = filesMap.stream()
                .map(fileMap -> {
                    String type = (String) fileMap.get("type");
                    String transferMethod = (String) fileMap.get("transfer_method");
                    
                    if ("remote_url".equals(transferMethod)) {
                        return FileObject.createRemoteUrl(type, (String) fileMap.get("url"));
                    } else {
                        return FileObject.createLocalFile(type, (String) fileMap.get("upload_file_id"));
                    }
                })
                .collect(Collectors.toList());
        }
        
        Boolean autoGenerateName = (Boolean) requestBody.get("auto_generate_name");
        
        return difyClient.chatBlocking(query, inputs, user, conversationId, files, autoGenerateName);
    }

    /**
     * 流式聊天接口
     * 
     * @param query 用户输入/提问内容
     * @param user 用户标识
     * @param conversationId 会话ID（可选）
     * @return SSE事件流
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam String query,
            @RequestParam String user,
            @RequestParam(required = false) String conversationId) {
        
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        String emitterId = user + "_" + System.currentTimeMillis();
        emitters.put(emitterId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));
        emitter.onError(e -> emitters.remove(emitterId));
        
        difyClient.chatStreaming(query, null, user, chunk -> {
            try {
                emitter.send(SseEmitter.event()
                    .name(chunk.getEvent())
                    .data(chunk, MediaType.APPLICATION_JSON));
                
                if ("message_end".equals(chunk.getEvent()) || "error".equals(chunk.getEvent())) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("发送SSE事件失败", e);
                emitter.completeWithError(e);
            }
        }, conversationId, null, null,null);
        
        return emitter;
    }

    /**
     * 停止响应
     * 
     * @param taskId 任务ID
     * @return 是否成功停止
     */
    @PostMapping("/stop")
    public boolean stopResponse(@RequestParam String taskId) {
        return difyClient.stopResponse(taskId);
    }

    /**
     * 上传文件
     * 
     * @param file 要上传的文件
     * @param user 用户标识
     * @return 上传成功后的文件对象
     */
    @PostMapping("/upload")
    public FileObject uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user") String user) {
        return difyClient.uploadFile(file, user);
    }
} 