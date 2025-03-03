package em.backend.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import em.backend.service.IDifyKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyKnowledgeServiceImpl implements IDifyKnowledgeService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Value("${dify.api-knowledge-key}")
    private String apiKnowledgeKey;

    @Value("${dify.api-key}")
    private String apiKey;

    @Value("${dify.api-url}")
    private String apiUrl;

    @Value("${dify.api-file-opea-key}")
    private String apiFileOpeaKey;

    @Override
    public KnowledgeResult createKnowledge(String description) {
        try {
            // 生成UUID作为知识库名称
            String knowledgeName = UUID.randomUUID().toString().replace("-", "");
            log.info("开始创建知识库: name={}, description={}", knowledgeName, description);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKnowledgeKey);

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("name", knowledgeName);
            requestBody.put("description", description);
            requestBody.put("indexing_technique", "high_quality"); // 使用高质量索引
            requestBody.put("permission", "all_team_members"); // 所有团队成员

            // 发送请求
            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
            String url = apiUrl + "/datasets";
            log.debug("发送创建知识库请求: url={}, body={}", url, requestBody);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSONObject.parseObject(response.getBody());
                log.info("创建知识库成功: response={}", result);
                return new KnowledgeResult(
                        result.getString("id"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getString("status")
                );
            } else {
                log.error("创建知识库失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("创建知识库异常", e);
            return null;
        }
    }

    @Override
    public boolean uploadFile(String knowledgeId, byte[] fileContent, String fileName) {
        try {
            log.info("开始上传文件: knowledgeId={}, fileName={}, fileSize={}", knowledgeId, fileName, fileContent.length);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKnowledgeKey);

            // 构建data参数
            String dataJson = "{\"indexing_technique\":\"high_quality\",\"process_rule\":{\"rules\":{\"pre_processing_rules\":[{\"id\":\"remove_extra_spaces\",\"enabled\":true},{\"id\":\"remove_urls_emails\",\"enabled\":true}],\"segmentation\":{\"separator\":\"###\",\"max_tokens\":500}},\"mode\":\"custom\"}}";

            // 构建multipart请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 添加data参数
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> jsonPart = new HttpEntity<>(dataJson, jsonHeaders);
            body.add("data", jsonPart);

            // 添加文件
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            fileHeaders.setContentDispositionFormData("file", fileName.replaceAll("\\.[^.]+$", ".md"));
            
            HttpEntity<byte[]> filePart = new HttpEntity<>(fileContent, fileHeaders);
            body.add("file", filePart);

            // 发送请求
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String url = apiUrl + "/datasets/" + knowledgeId + "/document/create-by-file";  // 修正URL
            log.debug("发送上传文件请求: url={}, fileName={}, dataJson={}", url, fileName, dataJson);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSONObject.parseObject(response.getBody());
                log.info("上传文件成功: response={}", result);
                return true;
            } else {
                log.error("上传文件失败: status={}, body={}, headers={}", 
                    response.getStatusCode(), 
                    response.getBody(),
                    response.getHeaders());
                return false;
            }
        } catch (Exception e) {
            log.error("上传文件异常", e);
            return false;
        }
    }

    @Override
    public String getKnowledgeStatus(String knowledgeId) {
        try {
            log.info("开始获取知识库状态: knowledgeId={}", knowledgeId);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);

            // 发送请求
            HttpEntity<?> request = new HttpEntity<>(headers);
            String url = apiUrl + "/knowledge-bases/" + knowledgeId;
            log.debug("发送获取知识库状态请求: url={}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            // 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSONObject.parseObject(response.getBody());
                String status = result.getString("status");
                log.info("获取知识库状态成功: knowledgeId={}, status={}", knowledgeId, status);
                return status;
            } else {
                log.error("获取知识库状态失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return "unknown";
            }
        } catch (Exception e) {
            log.error("获取知识库状态异常", e);
            return "error";
        }
    }

    @Override
    public String analyzeFileBlocking(byte[] fileContent, String fileName) {
        try {
            log.info("开始分析图片: fileName={}", fileName);

            // 先上传文件获取fileId
            String fileId = uploadFileForMessage(fileContent, fileName, "system");
            if (fileId == null) {
                log.error("文件上传失败，无法进行分析");
                return null;
            }

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiFileOpeaKey);

            // 根据文件名后缀判断文件类型
            String fileType = "file";
            if (fileName != null) {
                String extension = fileName.toLowerCase();
                if (extension.endsWith(".jpg") || extension.endsWith(".jpeg") ||
                        extension.endsWith(".png") || extension.endsWith(".gif")) {
                    fileType = "image";
                }
            }

            // 构建请求体
            JSONObject requestBody = new JSONObject();

            // 构建inputs对象
            JSONObject inputs = new JSONObject();
            JSONObject fileInput = new JSONObject();
            fileInput.put("type", fileType);
            fileInput.put("transfer_method", "local_file");
            fileInput.put("upload_file_id", fileId);
            inputs.put("file", fileInput);  // 使用"file"作为变量名
            requestBody.put("inputs", inputs);

            // 添加其他必需参数
            requestBody.put("response_mode", "blocking");
            requestBody.put("conversation_id", UUID.randomUUID().toString());
            requestBody.put("user", "fyjw");

            // 发送请求
            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
            String url = apiUrl + "/workflows/run";
            log.debug("发送图片分析请求: url={}, body={}", url, requestBody);

            // 设置10分钟超时
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10 * 60 * 1000); // 连接超时10分钟
            requestFactory.setReadTimeout(10 * 60 * 1000);    // 读取超时10分钟
            RestTemplate restTemplateWithTimeout = new RestTemplate(requestFactory);

            ResponseEntity<String> response = restTemplateWithTimeout.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject result = JSONObject.parseObject(response.getBody());

                JSONObject data = result.getJSONObject("data");
                log.debug("data: {}", data);
                JSONObject outputs = data.getJSONObject("outputs");
                String document = outputs.getString("document");
                String image = outputs.getString("image");
                String audio = outputs.getString("audio");
                String text = document == null ? image == null ? audio : image : document;
                log.info("图片分析成功: fileName={}, text={}", fileName, text);
                log.debug(result.toString());
                return text;
            } else {
                log.error("图片分析失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("文件分析异常", e);
            return null;
        }
    }

    @Override
    public String analyzeFileBlocking(File file) {
        try {
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file.toPath());
            return analyzeFileBlocking(fileContent, file.getName());
        } catch (Exception e) {
            log.error("读取文件失败: {}", file.getName(), e);
            return null;
        }
    }

    @Override
    public String uploadFileForMessage(File file, String userId) {
        try {
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file.toPath());
            return uploadFileForMessage(fileContent, file.getName(), userId);
        } catch (Exception e) {
            log.error("读取文件失败: {}", file.getName(), e);
            return null;
        }
    }

    @Override
    public String uploadFileForMessage(byte[] fileContent, String fileName, String userId) {
        try {
            log.info("开始上传文件: fileName={}, userId={}", fileName, userId);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiFileOpeaKey);

            // 构建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", fileResource);
            body.add("user", userId);

            // 发送请求
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            String url = apiUrl + "/files/upload";
            log.debug("发送上传文件请求: url={}", url);
            log.debug(request.toString());


            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 处理响应
            if ((response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED)
                    && response.getBody() != null) {
                JSONObject result = JSONObject.parseObject(response.getBody());
                String fileId = result.getString("id");
                log.info("文件上传成功: fileId={}", fileId);
                return fileId;
            } else {
                log.error("文件上传失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("文件上传异常", e);
            return null;
        }
    }

} 