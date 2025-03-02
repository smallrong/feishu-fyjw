package em.backend.service;

import lombok.Data;
import java.io.File;
import java.util.List;

/**
 * 飞书文件夹服务接口
 */
public interface IFeishuFolderService {
    
    @Data
    class FolderResult {
        private String token;
        private String url;
        
        public FolderResult(String token, String url) {
            this.token = token;
            this.url = url;
        }
    }
    
    @Data
    class FileInfo {
        private String fileToken;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String createTime;
        private String updateTime;
        
        public FileInfo(String fileToken, String fileName, String fileType, String createTime, String updateTime) {
            this.fileToken = fileToken;
            this.fileName = fileName;
            this.fileType = fileType;
            this.createTime = createTime;
            this.updateTime = updateTime;
        }
    }
    
    /**
     * 创建案件文件夹
     * @param caseName 案件名称
     * @param caseTime 案件时间
     * @param clientName 当事人
     * @return 文件夹信息，包含token和url，失败返回null
     */
    FolderResult createCaseFolder(String caseName, String caseTime, String clientName);

    /**
     * 设置文件夹权限
     * @param token 文件夹token
     * @param userId 用户ID
     * @return 是否设置成功
     */
    boolean setFolderPermission(String token, String userId);
    
    /**
     * 上传文件到飞书
     * 
     * @param file 文件对象
     * @param fileName 文件名称
     * @param fileType 文件类型
     * @return 文件的Key
     */
    String uploadFile(File file, String fileName, String fileType);
    
    /**
     * 获取文件夹下的所有文件
     * @param folderToken 文件夹token
     * @return 文件信息列表，如果获取失败返回空列表
     */
    List<FileInfo> getFolderFiles(String folderToken);
} 