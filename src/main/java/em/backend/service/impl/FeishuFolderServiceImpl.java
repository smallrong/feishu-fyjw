package em.backend.service.impl;

import com.lark.oapi.Client;
import com.lark.oapi.service.drive.v1.model.*;
import com.lark.oapi.service.im.v1.model.*;
import em.backend.service.IFeishuFolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuFolderServiceImpl implements IFeishuFolderService {

    private final Client feishuClient;

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
} 