package em.backend.dify.model;

import lombok.Data;

/**
 * 文件对象
 */
@Data
public class FileObject {
    private String type;
    private String transfer_method;
    private String url;
    private String upload_file_id;
    
    /**
     * 创建远程URL文件
     */
    public static FileObject createRemoteUrl(String type, String url) {
        FileObject file = new FileObject();
        file.setType(type);
        file.setTransfer_method("remote_url");
        file.setUrl(url);
        return file;
    }
    
    /**
     * 创建本地上传文件
     */
    public static FileObject createLocalFile(String type, String upload_file_id) {
        FileObject file = new FileObject();
        file.setType(type);
        file.setTransfer_method("local_file");
        file.setUpload_file_id(upload_file_id);
        return file;
    }
} 