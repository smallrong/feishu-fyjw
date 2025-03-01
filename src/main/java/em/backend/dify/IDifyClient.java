package em.backend.dify;

import em.backend.dify.model.ChatCompletionResponse;
import em.backend.dify.model.ChunkResponse;
import em.backend.dify.model.FileObject;
import em.backend.dify.model.workflow.WorkflowCompletionResponse;
import em.backend.dify.model.workflow.WorkflowChunkResponse;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dify API客户端接口
 */
public interface IDifyClient {

    /**
     * 执行聊天（阻塞模式）
     *
     * @param query 查询内容
     * @param inputs 变量值
     * @param user 用户ID
     * @param conversationId 会话ID（可选）
     * @param files 文件列表（可选）
     * @param autoGenerateName 是否自动生成会话名称（可选）
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
     * 执行聊天（流式模式）
     *
     * @param query 查询内容
     * @param inputs 变量值
     * @param user 用户ID
     * @param messageHandler 消息处理器
     * @param conversationId 会话ID（可选）
     * @param files 文件列表（可选）
     * @param autoGenerateName 是否自动生成会话名称（可选）
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
     * 停止聊天响应
     *
     * @param taskId 任务ID
     * @return 是否成功停止
     */
    boolean stopResponse(String taskId);

    /**
     * 上传文件（MultipartFile）
     *
     * @param file 文件
     * @param user 用户ID
     * @return 文件对象
     */
    FileObject uploadFile(MultipartFile file, String user);

    /**
     * 上传文件（File）
     *
     * @param file 文件
     * @param user 用户ID
     * @return 文件对象
     */
    FileObject uploadFile(File file, String user);

    /**
     * 执行工作流（阻塞模式）
     *
     * @param inputs 变量值
     * @param user 用户ID
     * @param files 文件列表（可选）
     * @param apiKey API密钥
     * @return 工作流完成响应
     */
    WorkflowCompletionResponse runWorkflowBlocking(
            Map<String, Object> inputs, String user, List<FileObject> files, String apiKey);

    /**
     * 执行工作流（流式模式）
     *
     * @param inputs 变量值
     * @param user 用户ID
     * @param messageHandler 消息处理器
     * @param files 文件列表（可选）
     * @param apiKey API密钥
     */
    void runWorkflowStreaming(
            Map<String, Object> inputs, String user, 
            Consumer<WorkflowChunkResponse> messageHandler, List<FileObject> files,
            String apiKey);

    /**
     * 停止工作流执行
     *
     * @param taskId 任务ID
     * @param apiKey API密钥
     * @return 是否成功停止
     */
    boolean stopWorkflow(String taskId, String apiKey);
}



