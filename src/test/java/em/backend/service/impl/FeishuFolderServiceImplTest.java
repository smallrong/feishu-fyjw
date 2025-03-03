//package em.backend.service.impl;
//
//import com.lark.oapi.Client;
//import em.backend.service.IFeishuFolderService.FileInfo;
//import em.backend.service.IMessageService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.Arrays;
//import java.util.Collections;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.*;
//
//class FeishuFolderServiceImplTest {
//
//    @Mock
//    private Client feishuClient;
//
//    @Mock
//    private IMessageService messageService;
//
//    private FeishuFolderServiceImpl feishuFolderService;
//
//    @TempDir
//    Path tempDir;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        feishuFolderService = new FeishuFolderServiceImpl(feishuClient, messageService);
//        ReflectionTestUtils.setField(feishuFolderService, "tempDir", tempDir.toString());
//    }
//
//    @Test
//    void testAnalyzeFilesAsync_EmptyFolder() throws Exception {
//        // 准备测试数据
//        String folderToken = "IyQpfrSMLlWJUAdlKw3cmcb3njf";
//        String openId = "ou_8e248fda8f4bff8499c1a7072767bfce";
//        String caseId = "19";
//
//        // Mock getFolderFiles返回空列表
//        FeishuFolderServiceImpl spyService = spy(feishuFolderService);
//        doReturn(Collections.emptyList()).when(spyService).getFolderFiles(folderToken);
//
//        // 执行测试
//        spyService.analyzeFilesAsync(folderToken, openId, caseId);
//
//        // 验证行为
//        verify(messageService).sendMessage(openId, "文件夹为空，无需分析", openId);
//    }
//
//    @Test
//    void testAnalyzeFilesAsync_MultipleFileTypes() throws Exception {
//        // 准备测试数据
//        String folderToken = "IyQpfrSMLlWJUAdlKw3cmcb3njf";
//        String openId = "ou_8e248fda8f4bff8499c1a7072767bfce";
//        String caseId = "19";
//
//        // 创建测试文件
//        createTempFile("程阿金和解通知.jpg", "test image");
//
//        // Mock getFolderFiles返回文件列表
//        FeishuFolderServiceImpl spyService = spy(feishuFolderService);
//        doReturn(Arrays.asList(
//            new FileInfo("I3Rlb09TSo6gUIxGXsucF7WQnOg", "程阿金和解通知.jpg", "file", "1740908237", "1740908237")
//        )).when(spyService).getFolderFiles(folderToken);
//
//        // 执行测试
//        spyService.analyzeFilesAsync(folderToken, openId, caseId);
//
//        // 验证行为
//        verify(messageService).sendMessage(eq(openId), contains("开始分析文件，共1个文件"), eq(openId));
//        verify(messageService, atLeastOnce()).sendMessage(eq(openId), contains("开始分析:"), eq(openId));
//        verify(messageService, atLeastOnce()).sendMessage(eq(openId), contains("完成分析:"), eq(openId));
//        verify(messageService).sendMessage(eq(openId), eq("文件分析完成"), eq(openId));
//    }
//
//    @Test
//    void testAnalyzeFilesAsync_DownloadError() throws Exception {
//        // 准备测试数据
//        String folderToken = "folder_123";
//        String openId = "user_123";
//        String caseId = "case_123";
//
//        // Mock getFolderFiles返回文件列表
//        FeishuFolderServiceImpl spyService = spy(feishuFolderService);
//        doReturn(Arrays.asList(
//            new FileInfo("doc_token", "test.docx", "docx", "2024-01-01 00:00:00", "2024-01-01 00:00:00")
//        )).when(spyService).getFolderFiles(folderToken);
//
//        // Mock Client抛出异常
//        doThrow(new IOException("API调用失败")).when(spyService).getFolderFiles(any());
//
//        // 执行测试
//        spyService.analyzeFilesAsync(folderToken, openId, caseId);
//
//        // 验证行为
//        verify(messageService, atLeastOnce()).sendMessage(eq(openId), contains("分析文件失败:"), eq(openId));
//    }
//
//    private File createTempFile(String fileName, String content) throws IOException {
//        Path filePath = tempDir.resolve(fileName);
//        Files.write(filePath, content.getBytes());
//        return filePath.toFile();
//    }
//}