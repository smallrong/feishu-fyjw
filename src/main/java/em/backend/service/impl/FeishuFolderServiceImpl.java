package em.backend.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.lark.oapi.Client;
import com.lark.oapi.service.bitable.BitableService;
import com.lark.oapi.service.drive.v1.model.*;
import com.lark.oapi.service.im.v1.model.*;
import com.lark.oapi.service.bitable.v1.model.*;
import em.backend.pojo.UserStatus;
import em.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import em.backend.pojo.CaseInfo;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;

import com.lark.oapi.service.bitable.v1.model.BatchCreateAppTableRecordReq;
import com.lark.oapi.service.bitable.v1.model.BatchCreateAppTableRecordReqBody;
import com.lark.oapi.service.bitable.v1.model.BatchCreateAppTableRecordResp;
import com.lark.oapi.service.bitable.v1.model.AppTableRecord;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuFolderServiceImpl implements IFeishuFolderService {

    private final Client feishuClient;
    private final IMessageService messageService;
    private final IDifyKnowledgeService difyService;
    private final ICardTemplateService cardTemplateService;
//    private final ICaseService caseService;

    @Value("${temp.dir:./temp}")
    private String tempDir;


    @Override
    public FolderResult createCaseFolder(String caseName, String caseTime, String clientName) {
        try {
            // 构造文件夹名称：案件名称_当事人_时间
            String folderName = String.format("%s_%s_%s", caseName, clientName, caseTime.split(" ")[0]);

            // 构造请求
            CreateFolderFileReq req = CreateFolderFileReq.newBuilder()
                    .createFolderFileReqBody(CreateFolderFileReqBody.newBuilder()
                            .name(folderName)
                            .folderToken("") // 在根目录创建
                            .build())
                    .build();

            // 调用API
            CreateFolderFileResp resp = feishuClient.drive().v1().file().createFolder(req);

            // 处理响应
            if (resp.success()) {
                String token = resp.getData().getToken();
                String url = resp.getData().getUrl();
                log.info("案件文件夹创建成功: {}, url: {}", folderName, url);
                return new FolderResult(token, url);
            } else {
                log.error("创建文件夹失败, code: {}, msg: {}, reqId: {}",
                        resp.getCode(), resp.getMsg(), resp.getRequestId());
                return null;
            }
        } catch (Exception e) {
            log.error("创建文件夹异常", e);
            return null;
        }
    }

    @Override
    public boolean setFolderPermission(String token, String userId) {
        try {
            // 创建请求对象
            CreatePermissionMemberReq req = CreatePermissionMemberReq.newBuilder()
                    .token(token)
                    .type("folder")  // 使用正确的文件夹类型
                    .baseMember(BaseMember.newBuilder()
                            .memberType("openid")
                            .memberId(userId)
                            .perm("full_access")  // 设置为管理权限
                            .permType("container")  // 当前页面及子页面
                            .type("user")
                            .build())
                    .build();

            // 发起请求
            CreatePermissionMemberResp resp = feishuClient.drive().v1().permissionMember().create(req);

            // 处理响应
            if (resp.success()) {
                log.info("文件夹权限设置成功, token: {}, userId: {}", token, userId);
                return true;
            } else {
                log.error("设置文件夹权限失败, code: {}, msg: {}, reqId: {}",
                        resp.getCode(), resp.getMsg(), resp.getRequestId());
                return false;
            }
        } catch (Exception e) {
            log.error("设置文件夹权限异常", e);
            return false;
        }
    }

    @Override
    public String uploadFile(File file, String fileName, String fileType) {
        try {
            log.info("上传文件: fileName={}, fileType={}", fileName, fileType);

            // 构建上传文件请求
            CreateFileReq req = CreateFileReq.newBuilder()
                .createFileReqBody(CreateFileReqBody.newBuilder()
                    .fileType(fileType)
                    .fileName(fileName)
                    .file(file)
                    .build())
                .build();

            // 发送请求
            CreateFileResp resp = feishuClient.im().v1().file().create(req);

            // 处理响应
            if (!resp.success()) {
                log.error("上传文件失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return null;
            }

            // 返回文件Key
            String fileKey = resp.getData().getFileKey();
            log.info("上传文件成功: fileKey={}", fileKey);
            return fileKey;
        } catch (Exception e) {
            log.error("上传文件异常", e);
            return null;
        }
    }

    public List<FileInfo> getFolderFiles(String folderToken) {
       try {
           // 构造请求对象
           ListFileReq req = ListFileReq.newBuilder()
                   .folderToken(folderToken)
                   .build();

           // 调用API获取文件列表
           ListFileResp resp = feishuClient.drive().v1().file().list(req);
           log.info("获取文件列表响应: {}", JSON.toJSONString(resp));
           
           // 处理响应
           if (resp.success()) {
               if (resp.getData() != null && resp.getData().getFiles() != null) {
                   return Arrays.stream(resp.getData().getFiles())
                           .filter(file -> file != null)
                           .map(file -> new FileInfo(
                                   file.getToken(),
                                   file.getName(),
                                   file.getType(),
                                   String.valueOf(file.getCreatedTime()),
                                   String.valueOf(file.getModifiedTime())
                           ))
                           .collect(Collectors.toList());
               } else {
                   log.warn("文件列表为空: folderToken={}", folderToken);
                   return Collections.emptyList();
               }
           } else {
               log.error("获取文件列表失败, code: {}, msg: {}, reqId: {}",
                       resp.getCode(), resp.getMsg(), resp.getRequestId());
               return Collections.emptyList();
           }
       } catch (Exception e) {
           log.error("获取文件列表异常", e);
           return Collections.emptyList();
       }
    }

    @Async
    @Override
    public void analyzeFilesAsync(String folderToken, String openId, String caseId, String caseName, String difyKnowledgeId) {
        try {
            log.info("开始异步分析文件: folderToken={}, openId={}, caseId={}", folderToken, openId, caseId);
            
            // 1. 获取文件夹中的所有文件
            List<FileInfo> files = getFolderFiles(folderToken);
            log.info("获取到的文件列表: {}", files);
            if (files.isEmpty()) {
                log.warn("文件夹为空，无需分析: folderToken={}", folderToken);
                messageService.sendMessage(openId, "文件夹为空，无需分析", openId);
                return;
            }
            
            // 2. 创建临时目录
            File tempDirectory = new File(tempDir, caseId);
            if (!tempDirectory.exists()) {
                tempDirectory.mkdirs();
            }


            
            for (FileInfo file : files) {
                try {
                    String fileType = getFileType(file.getFileName());
                    
                    if (isFolder(fileType)) {
                        // 递归分析子文件夹
                        analyzeFilesAsync(file.getFileToken(), openId, caseId, caseName, difyKnowledgeId);
                    } else {
                        // 下载文件到临时目录
                        File downloadedFile = downloadFile(file, tempDirectory);
                        if (downloadedFile != null) {
                            try {
                                String analysisResult = null;
                                String curFileTypeChi = "文档";
                                if (isDocument(fileType)) {
                                    analysisResult = analyzeDocument(downloadedFile, fileType, openId);
                                } else if (isPdf(fileType)) {
                                    analysisResult = analyzePdf(downloadedFile, openId, difyKnowledgeId);
                                } else if (isImage(fileType)) {
                                    analysisResult = analyzeImage(downloadedFile, openId, difyKnowledgeId);
                                    curFileTypeChi = "图片";
                                } else if (isAudio(fileType)) {
                                    analysisResult = analyzeAudio(downloadedFile, openId, difyKnowledgeId);
                                    curFileTypeChi = "音频";
                                } else if (isVideo(fileType)) {
                                    analysisResult = analyzeVideo(downloadedFile, openId, difyKnowledgeId);
                                } else if (isArchive(fileType)) {
                                    analysisResult = analyzeArchive(downloadedFile, openId, caseId, tempDirectory, difyKnowledgeId);
                                } else {
                                    log.info("不支持的文件类型: {}", fileType);
                                }
                                
                                // 将分析结果添加到记录中
                                if (analysisResult != null) {
                                    Map<String, Object> record = new HashMap<>();
                                    record.put("原始文件名", file.getFileName());
                                    record.put("文件生成时间", System.currentTimeMillis());
                                    record.put("文件类型", curFileTypeChi);
                                    record.put("文件内容", analysisResult);
                                    record.put("创建人", openId);


                                    uploadToMultiSheet("AY2JbDhHBaU7HYsvjMncKMh7n6Y", "tblXyJKE7HZlDWFE", openId,record);
                                }
                            } finally {
                                downloadedFile.delete();
                            }
                        }
                    }
                    
                    messageService.sendMessage(openId, 
                        String.format("完成分析: %s", file.getFileName()), openId);
                        
                } catch (Exception e) {
                    log.error("分析文件失败: {}", file.getFileName(), e);
                    messageService.sendMessage(openId, 
                        String.format("分析文件失败: %s, 原因: %s", file.getFileName(), e.getMessage()), openId);
                }
            }


            // 5. 清理临时目录
            try {
                FileUtils.deleteDirectory(tempDirectory);
            } catch (Exception e) {
                log.error("清理临时目录失败", e);
            }

            // 6. 发送分析完成的消息
            messageService.sendCardMessage(openId, cardTemplateService.buildMessageCard("文件分析完毕", caseName));
            
        } catch (Exception e) {
            log.error("文件分析过程发生异常", e);
            messageService.sendMessage(openId, "文件分析过程发生异常: " + e.getMessage(), openId);
        }
    }
    
    /**
     * 下载文件到临时目录
     */
    private File downloadFile(FileInfo file, File tempDirectory) {
        try {
            // 构造下载请求
            DownloadFileReq req = DownloadFileReq.newBuilder()
                    .fileToken(file.getFileToken())
                    .build();
            
            // 发送请求
            DownloadFileResp resp = feishuClient.drive().v1().file().download(req);
            
            if (resp.success()) {
                // 创建临时文件
                File tempFile = new File(tempDirectory, file.getFileName());
                // 将响应内容写入临时文件
                ByteArrayOutputStream baos = resp.getData();
                try (ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray())) {
                    Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return tempFile;
            } else {
                log.error("下载文件失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return null;
            }
        } catch (Exception e) {
            log.error("下载文件异常", e);
            return null;
        }
    }
    
    private boolean isFolder(String fileType) {
        return fileType.isEmpty();  // 如果没有后缀名，认为是文件夹
    }
    private boolean isPdf(String fileType) {
        String[] docTypes = {"pdf"};
        return Arrays.asList(docTypes).contains(fileType.toLowerCase());
    }
    
    private boolean isDocument(String fileType) {
        String[] docTypes = {"doc", "docx",  "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx"};
        return Arrays.asList(docTypes).contains(fileType.toLowerCase());
    }
    
    private boolean isImage(String fileType) {
        String[] imageTypes = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff"};
        return Arrays.asList(imageTypes).contains(fileType.toLowerCase());
    }
    
    private boolean isAudio(String fileType) {
        String[] audioTypes = {"mp3", "wav", "ogg", "m4a", "wma", "aac"};
        return Arrays.asList(audioTypes).contains(fileType.toLowerCase());
    }
    
    private boolean isVideo(String fileType) {
        String[] videoTypes = {"mp4", "avi", "mov", "wmv", "flv", "mkv"};
        return Arrays.asList(videoTypes).contains(fileType.toLowerCase());
    }
    
    private boolean isArchive(String fileType) {
        String[] archiveTypes = {"zip", "rar", "7z"};
        return Arrays.asList(archiveTypes).contains(fileType.toLowerCase());
    }
    
    /**
     * 分析文档文件
     */
    private String analyzeDocument(File file, String fileType, String openId) {
        // TODO: 实现文档分析逻辑
        log.info("分析文档: {}", file.getName());
        messageService.sendMessage(openId, String.format("正在分析文档: %s", file.getName()), openId);
        return "文档分析结果"; // 返回实际分析结果
    }
    
    /**
     * 分析图片文件
     */
    private String analyzeImage(File file, String openId, String difyKnowledgeId) {
        try {
            log.info("开始分析图片: {}", file.getName());
            messageService.sendMessage(openId, String.format("开始分析图片: %s", file.getName()), openId);

            // 1. 调用dify工作流进行图片分析
            String analysisResult = difyService.analyzeFileBlocking(file);
            if (analysisResult == null) {
                log.error("图片分析失败: {}", file.getName());
                messageService.sendMessage(openId, String.format("图片分析失败: %s", file.getName()), openId);
                return null;
            }
//            CaseInfo currentCaseInfo = caseService.getCurrentCaseInfo(openId);


            // 3. 上传到知识库
            boolean uploadSuccess = difyService.uploadFile(difyKnowledgeId, analysisResult.getBytes(), file.getName());
            if (!uploadSuccess) {
                log.error("上传到知识库失败: {}", file.getName());
                messageService.sendMessage(openId, String.format("上传到知识库失败: %s", file.getName()), openId);
                return null;
            }
            
            log.info("图片分析和上传完成: {}", file.getName());
            messageService.sendMessage(openId, String.format("图片分析和上传完成: %s", file.getName()), openId);

            return analysisResult;
        } catch (Exception e) {
            log.error("图片处理过程发生异常: {}", file.getName(), e);
            messageService.sendMessage(openId, String.format("图片处理异常: %s, 原因: %s", 
                file.getName(), e.getMessage()), openId);
            return null;
        }
    }
    
    /**
     * 分析音频文件
     */
    private String analyzeAudio(File file, String openId, String difyKnowledgeId) {
        try {
            log.info("开始分析音频: {}", file.getName());
            messageService.sendMessage(openId, String.format("开始分析音频: %s", file.getName()), openId);

            // 1. 调用dify工作流进行图片分析
            String analysisResult = difyService.analyzeFileBlocking(file);
            if (analysisResult == null) {
                log.error("音频分析失败: {}", file.getName());
                messageService.sendMessage(openId, String.format("音频分析失败: %s", file.getName()), openId);
                return null;
            }
//            CaseInfo currentCaseInfo = caseService.getCurrentCaseInfo(openId);


            // 3. 上传到知识库
            boolean uploadSuccess = difyService.uploadFile(difyKnowledgeId, analysisResult.getBytes(), file.getName());
            if (!uploadSuccess) {
                log.error("上传到知识库失败: {}", file.getName());
                messageService.sendMessage(openId, String.format("上传到知识库失败: %s", file.getName()), openId);
                return null;
            }

            log.info("音频分析和上传完成: {}", file.getName());
            messageService.sendMessage(openId, String.format("音频分析和上传完成: %s", file.getName()), openId);

            return analysisResult;
        } catch (Exception e) {
            log.error("音频处理过程发生异常: {}", file.getName(), e);
            messageService.sendMessage(openId, String.format("音频处理异常: %s, 原因: %s",
                    file.getName(), e.getMessage()), openId);
            return null;
        }
    }
    
    /**
     * 分析视频文件
     */
    private String analyzeVideo(File file, String openId, String difyKnowledgeId) {
        // TODO: 使用视频处理库进行分析
        // 例如：Xuggler, JavaCV
        log.info("分析视频: {}", file.getName());
        messageService.sendMessage(openId, String.format("正在分析视频: %s", file.getName()), openId);
        return null;
    }



    /**
     * 分析视频文件
     */
    private String analyzePdf(File file, String openId, String difyKnowledgeId) {
        try {
            log.info("开始分析Pdf: {}", file.getName());
            messageService.sendMessage(openId, String.format("开始分析Pdf: %s", file.getName()), openId);

            // 1. 调用dify工作流进行图片分析
            String analysisResult = difyService.analyzeFileBlocking(file);
            if (analysisResult == null || analysisResult.isEmpty()) {
                log.info("PDF可能是图片类型,尝试转换为图片后分析: {}", file.getName());
                messageService.sendMessage(openId, String.format("PDF是图片类型,正在转换并分析: %s", file.getName()), openId);
                
                // 使用PDFBox加载PDF文档
                PDDocument document = PDDocument.load(file);
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                
                StringBuilder combinedResult = new StringBuilder();
                
                // 遍历每一页PDF
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    // 将PDF页面渲染为图片
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300); // 300 DPI for good quality
                    
                    // 将BufferedImage转换为临时文件
                    File tempImageFile = new File(tempDir, String.format("%s_page_%d.png", file.getName(), page));
                    ImageIO.write(image, "PNG", tempImageFile);
                    
                    try {
                        // 分析转换后的图片
                        String pageResult = difyService.analyzeFileBlocking(tempImageFile);
                        if (pageResult != null && !pageResult.isEmpty()) {
                            combinedResult.append(pageResult).append("\n");
                        }
                    } finally {
                        // 删除临时图片文件
                        tempImageFile.delete();
                    }
                }
                
                // 关闭PDF文档
                document.close();
                
                // 使用合并后的结果
                analysisResult = combinedResult.toString();
                
                // 如果所有页面分析都失败
                if (analysisResult.isEmpty()) {
                    log.error("PDF所有页面分析均失败: {}", file.getName());
                    messageService.sendMessage(openId, String.format("PDF分析失败: %s", file.getName()), openId);
                    return null;
                }
            }
//            CaseInfo currentCaseInfo = caseService.getCurrentCaseInfo(openId);

            log.debug("analysisResult : " , analysisResult);
            // 3. 上传到知识库
            boolean uploadSuccess = difyService.uploadFile(difyKnowledgeId, analysisResult.getBytes(), file.getName());
            if (!uploadSuccess) {
                log.error("上传到知识库失败: {}", file.getName());
                messageService.sendMessage(openId, String.format("上传到知识库失败: %s", file.getName()), openId);
                return null;
            }

            log.info("音频分析和上传完成: {}", file.getName());
            messageService.sendMessage(openId, String.format("音频分析和上传完成: %s", file.getName()), openId);

            return analysisResult;
        } catch (Exception e) {
            log.error("PDF处理过程发生异常: {}", file.getName(), e);
            messageService.sendMessage(openId, String.format("PDF处理异常: %s, 原因: %s",
                    file.getName(), e.getMessage()), openId);
            return null;
        }
    }
    
    /**
     * 分析压缩文件
     */
    private String analyzeArchive(File file, String openId, String caseId, File tempDirectory, String difyKnowledgeId) {
        try {
            log.info("开始解压文件: {}", file.getName());
            messageService.sendMessage(openId, String.format("正在解压文件: %s", file.getName()), openId);
            
            // 创建解压目录
            File extractDir = new File(tempDirectory, "extract_" + System.currentTimeMillis());
            extractDir.mkdirs();
            
            // 解压文件
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file.toPath()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        // 创建目标文件
                        File targetFile = new File(extractDir, entry.getName());
                        // 确保目标文件的父目录存在
                        targetFile.getParentFile().mkdirs();
                        // 复制文件内容
                        Files.copy(zis, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        // 分析解压出的文件
                        String fileType = getFileType(targetFile.getName());
                        if (isDocument(fileType)) {
                            return analyzeDocument(targetFile, fileType, openId);
                        } else if (isPdf(fileType)) {
                            return analyzePdf(targetFile, openId, difyKnowledgeId);
                        } else if (isImage(fileType)) {
                            return analyzeImage(targetFile, openId, difyKnowledgeId);
                        } else if (isAudio(fileType)) {
                            return analyzeAudio(targetFile, openId, difyKnowledgeId);
                        } else if (isVideo(fileType)) {
                            return analyzeVideo(targetFile, openId, difyKnowledgeId);
                        }
                    }
                }
            }
            
            // 清理解压目录
            FileUtils.deleteDirectory(extractDir);
            
            return null;
        } catch (Exception e) {
            log.error("解压文件失败: {}", file.getName(), e);
            messageService.sendMessage(openId, String.format("解压文件失败: %s", file.getName()), openId);
            return null;
        }
    }
    
    /**
     * 获取文件类型（后缀名）
     */
    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }

    @Override
    public boolean uploadToMultiSheet(String appToken, String tableId,String openId,Map<String, Object> records) {
        try {
            log.info("开始上传数据到多维表格: appToken={}, tableId={}, recordCount={}", 
                appToken, tableId, records.size());


            CreateAppTableRecordReq req = CreateAppTableRecordReq.newBuilder()
                    .appToken(appToken)
                    .tableId(tableId)
                    .appTableRecord(AppTableRecord.newBuilder()
                            .fields(records).build())
                    .build();
            // 调用API
            CreateAppTableRecordResp resp = feishuClient.bitable().v1().appTableRecord().create(req);
            

            // 处理响应
            if (resp.success()) {
                log.info("成功上传记录到多维表格");
                return true;
            } else {
                log.error("数据上传失败: code={}, msg={}, reqId={}", 
                    resp.getCode(), resp.getMsg(), resp.getRequestId());
                return false;
            }
        } catch (Exception e) {
            log.error("上传数据到多维表格异常", e);
            return false;
        }
    }
}