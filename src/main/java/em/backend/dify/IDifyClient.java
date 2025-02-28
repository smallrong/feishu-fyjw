package em.backend.dify;

import em.backend.dify.model.ChatCompletionResponse;
import em.backend.dify.model.ChunkResponse;
import em.backend.dify.model.FileObject;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.web.multipart.MultipartFile;

/**
 * Dify API 客户端接口
 */
public interface IDifyClient {

    // TODO 测试流式对话正常
    // TODO 其余未测试

    /**
     * 发送对话消息（阻塞模式）
     * 
     * @param query 用户输入/提问内容
     * @param inputs 变量值，允许传入 App 定义的各变量值
     * @param user 用户标识
     * @param conversationId 会话ID（可选）
     * @param files 上传的文件（可选）
     * @param autoGenerateName 是否自动生成标题（可选，默认true）
     * @return 聊天完成响应
     */
    ChatCompletionResponse chatBlocking(
            String query, 
            Map<String, Object> inputs, 
            String user, 
            String conversationId, 
            List<FileObject> files,
            Boolean autoGenerateName);
    
    /**
     * 发送对话消息（流式模式）
     * 
     * @param query 用户输入/提问内容
     * @param inputs 变量值，允许传入 App 定义的各变量值
     * @param user 用户标识
     * @param messageHandler 消息处理器，用于处理流式返回的消息块
     * @param conversationId 会话ID（可选）
     * @param files 上传的文件（可选）
     * @param autoGenerateName 是否自动生成标题（可选，默认true）
     */
    void chatStreaming(
            String query, 
            Map<String, Object> inputs, 
            String user, 
            Consumer<ChunkResponse> messageHandler,
            String conversationId, 
            List<FileObject> files, 
            Boolean autoGenerateName);
    
    /**
     * 停止响应
     * 
     * @param taskId 任务ID
     * @return 是否成功停止
     */
    boolean stopResponse(String taskId);

    /**
     * 上传文件（目前仅支持图片）
     * 支持 png, jpg, jpeg, webp, gif 格式
     * 
     * @param file 要上传的文件
     * @param user 用户标识，必须和发送消息接口传入 user 保持一致
     * @return 上传成功后的文件对象
     */
    FileObject uploadFile(File file, String user);

    /**
     * 上传文件（使用MultipartFile，适用于Spring MVC）
     * 支持 png, jpg, jpeg, webp, gif 格式
     * 
     * @param file 要上传的文件（MultipartFile格式）
     * @param user 用户标识，必须和发送消息接口传入 user 保持一致
     * @return 上传成功后的文件对象
     */
    FileObject uploadFile(MultipartFile file, String user);
} 