package em.backend.service.impl;

import com.alibaba.fastjson2.JSONObject;
import em.backend.service.IDifyKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyKnowledgeServiceImpl implements IDifyKnowledgeService {

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${dify.api-knowledge-key}")
    private String apiKey;
    
    @Value("${dify.api-url}")
    private String apiUrl;

    @Override
    public KnowledgeResult createKnowledge(String description) {
        try {
            // 生成UUID作为知识库名称
            String knowledgeName = UUID.randomUUID().toString().replace("-", "");
            log.info("开始创建知识库: name={}, description={}", knowledgeName, description);
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

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
            log.info("开始上传文件: knowledgeId={}, fileName={}", knowledgeId, fileName);
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", fileResource);

            // 发送请求
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String url = apiUrl + "/knowledge-bases/" + knowledgeId + "/documents";
            log.debug("发送上传文件请求: url={}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 处理响应
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("文件上传成功: knowledgeId={}, fileName={}", knowledgeId, fileName);
                return true;
            } else {
                log.error("文件上传失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("文件上传异常", e);
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
} 