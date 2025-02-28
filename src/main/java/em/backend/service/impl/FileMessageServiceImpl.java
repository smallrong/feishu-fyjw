package em.backend.service.impl;

import com.lark.oapi.Client;
import com.lark.oapi.service.drive.v1.model.DownloadFileReq;
import com.lark.oapi.service.drive.v1.model.DownloadFileResp;
import com.lark.oapi.service.drive.v1.model.UploadAllFileReq;
import com.lark.oapi.service.drive.v1.model.UploadAllFileResp;
import com.lark.oapi.service.drive.v1.model.UploadAllFileReqBody;
import com.lark.oapi.service.im.v1.model.GetFileReq;
import com.lark.oapi.service.im.v1.model.GetFileResp;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import em.backend.pojo.CaseInfo;
import em.backend.pojo.UserStatus;
import em.backend.service.ICaseService;
import em.backend.service.IFileMessageService;
import em.backend.service.IMessageService;
import em.backend.service.IUserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileMessageServiceImpl implements IFileMessageService {
    
    private final IUserStatusService userStatusService;
    private final ICaseService caseService;
    private final Client feishuClient;
    private final IMessageService messageService;
    
    @Override
    public boolean handleFileMessage(String userId, String chatId, String messageId, String fileKey, String fileName) {
        File tempFile = null;
        try {
            log.info("开始处理文件消息: userId=[{}], chatId=[{}], messageId=[{}], fileKey=[{}], fileName=[{}]", 
                    userId, chatId, messageId, fileKey, fileName);

            // 1. 查询用户当前案件状态
            UserStatus userStatus = userStatusService.lambdaQuery()
                    .eq(UserStatus::getOpenId, userId)
                    .one();
            
            if (userStatus == null || userStatus.getCurrentCaseId() == null) {
                log.warn("用户未选择案件: userId=[{}]", userId);
                messageService.sendMessage(chatId, "请先选择一个案件", userId);
                return false;
            }
            log.debug("查询到用户状态: userStatus=[{}]", userStatus);
            
            // 2. 查询案件信息
            CaseInfo caseInfo = caseService.getById(userStatus.getCurrentCaseId());
            if (caseInfo == null) {
                log.error("案件不存在: caseId=[{}]", userStatus.getCurrentCaseId());
                messageService.sendMessage(chatId, "当前案件不存在", userId);
                return false;
            }
            log.debug("查询到案件信息: caseInfo=[{}]", caseInfo);
            
            // 3. 从文件夹URL中提取token
            String folderToken = extractFolderToken(caseInfo.getFolderUrl());
            if (folderToken == null) {
                log.error("无效的文件夹URL: {}", caseInfo.getFolderUrl());
                messageService.sendMessage(chatId, "案件文件夹地址无效", userId);
                return false;
            }
            log.debug("提取到文件夹token: folderToken=[{}]", folderToken);

            // 4. 下载文件
            log.info("开始下载文件: fileKey=[{}], messageId=[{}]", fileKey, messageId);
            tempFile = downloadFile(fileKey, messageId);
            if (tempFile == null) {
                log.error("下载文件失败: fileKey=[{}]", fileKey);
                messageService.sendMessage(chatId, "文件下载失败", userId);
                return false;
            }
            log.debug("文件下载成功: tempFile=[{}], size=[{}]bytes", tempFile.getPath(), tempFile.length());

            // 检查文件大小
            long fileSize = tempFile.length();
            if (fileSize > 20 * 1024 * 1024) { // 20MB
                log.error("文件过大: size=[{}]bytes", fileSize);
                messageService.sendMessage(chatId, "文件大小超过20MB限制", userId);
                return false;
            }

            // 5. 上传文件到指定文件夹
            log.info("开始上传文件到目标文件夹: folderToken=[{}]", folderToken);
            UploadAllFileReq req = UploadAllFileReq.newBuilder()
                    .uploadAllFileReqBody(UploadAllFileReqBody.newBuilder()
                            .fileName(fileName)
                            .parentType("explorer")
                            .parentNode(folderToken)
                            .size((int)fileSize)
                            .file(tempFile)
                            .build())
                    .build();

            // 发送请求
            UploadAllFileResp resp = feishuClient.drive().v1().file().uploadAll(req);

            // 处理响应
            if (!resp.success()) {
                log.error("上传文件失败: code=[{}], msg=[{}], reqId=[{}]", 
                        resp.getCode(), resp.getMsg(), resp.getRequestId());
                
                String errorMsg;
                switch (resp.getCode()) {
                    case 1061045:
                        errorMsg = "操作太频繁，请稍后重试";
                        break;
                    case 1061001:
                        errorMsg = "文件不存在或已被删除";
                        break;
                    case 1061009:
                        errorMsg = "没有权限操作该文件";
                        break;
                    default:
                        errorMsg = "文件上传失败";
                        break;
                }
                
                messageService.sendMessage(chatId, errorMsg, userId);
                return false;
            }
            
            log.info("文件处理完成: fileKey=[{}]", fileKey);
            // 发送成功消息
            messageService.sendMessage(chatId, 
                    String.format("文件已上传到案件「%s」的文件夹中", caseInfo.getCaseName()), 
                    userId);
            return true;
            
        } catch (Exception e) {
            log.error("处理文件消息异常: fileKey=[{}], error=[{}]", fileKey, e.getMessage(), e);
            messageService.sendMessage(chatId, "处理文件失败", userId);
            return false;
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("清理临时文件: file=[{}], deleted=[{}]", tempFile.getPath(), deleted);
            }
        }
    }
    
    /**
     * 从文件夹URL中提取token
     */
    private String extractFolderToken(String folderUrl) {
        if (folderUrl == null || folderUrl.isEmpty()) {
            return null;
        }
        
        Pattern pattern = Pattern.compile("/folder/([^/]+)/?$");
        Matcher matcher = pattern.matcher(folderUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 下载文件到临时目录
     */
    private File downloadFile(String fileKey, String messageId) {
        try {
            log.debug("开始下载文件: fileKey=[{}], messageId=[{}]", fileKey, messageId);
            // 创建临时文件
            Path tempFile = Files.createTempFile("feishu_", ".tmp");
            log.debug("创建临时文件: path=[{}]", tempFile);
            
            // 创建下载请求
            GetMessageResourceReq req = GetMessageResourceReq.newBuilder()
                    .messageId(messageId)
                    .fileKey(fileKey)
                    .type("file")  // 因为是文件消息，所以类型是 file
                    .build();
            
            // 发送请求
            log.debug("发送下载请求...");
            GetMessageResourceResp resp = feishuClient.im().v1().messageResource().get(req);
            
            if (!resp.success()) {
                // 详细记录错误信息
                log.error("下载文件失败: code=[{}], msg=[{}], reqId=[{}], fileKey=[{}]", 
                        resp.getCode(), resp.getMsg(), resp.getRequestId(), fileKey);
                return null;
            }
            
            // 将文件内容写入临时文件
            log.debug("开始写入文件内容...");
            resp.writeFile(tempFile.toString());
            
            // 验证文件是否成功写入
            File file = tempFile.toFile();
            if (!file.exists() || file.length() == 0) {
                log.error("文件下载成功但写入失败或为空: path=[{}]", tempFile);
                return null;
            }
            
            log.debug("文件下载完成: path=[{}], size=[{}]bytes", file.getPath(), file.length());
            return file;
            
        } catch (Exception e) {
            log.error("下载文件异常: fileKey=[{}], error=[{}]", fileKey, e.getMessage(), e);
            return null;
        }
    }
}