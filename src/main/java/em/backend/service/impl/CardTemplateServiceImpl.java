package em.backend.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import em.backend.pojo.CaseInfo;
import em.backend.service.ICardTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CardTemplateServiceImpl implements ICardTemplateService {

    @Override
    public String buildCreateCaseCard() {
        try {
            ClassPathResource resource = new ClassPathResource("template/createCase.json");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("构建创建案件卡片失败", e);
            return null;
        }
    }


    @Override
    public String buildSelectCaseCard(String currentCase, List<CaseInfo> cases, String callbackName) {
        // 将CaseInfo列表转换为Map<String, String>列表
        List<Map<String, String>> caseList = new ArrayList<>();
        for (CaseInfo caseInfo : cases) {
            Map<String, String> map = new HashMap<>();
            map.put("name", caseInfo.getCaseName());
            map.put("id", String.valueOf(caseInfo.getId()));
            caseList.add(map);
        }
        try {
            // 创建模板变量
            Map<String, Object> templateVariables = new HashMap<>();

            // 设置当前案件
            templateVariables.put("case", currentCase);

            // 设置回调名称
            templateVariables.put("callback_name", callbackName);

            // 设置案件列表，按照飞书卡片模板要求的格式
            List<Map<String, String>> options = new ArrayList<>();
            for (Map<String, String> caseInfo : caseList) {
                Map<String, String> option = new HashMap<>();
                option.put("text", caseInfo.get("name"));
                option.put("value", caseInfo.get("id"));
                options.add(option);
            }
            templateVariables.put("My_options", options);

            // 创建卡片模板数据
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("template_id", "AAqB3OtipZWJR"); // 替换为实际的模板ID
            templateData.put("template_variable", templateVariables);

            // 创建完整的卡片内容
            Map<String, Object> cardContent = new HashMap<>();
            cardContent.put("type", "template");
            cardContent.put("data", templateData);

            // 转换为JSON字符串
            return JSON.toJSONString(cardContent);
        } catch (Exception e) {
            log.error("生成选择案件卡片失败", e);
            return null;
        }
    }

    @Override
    public String buildMessageCard(String content, String currentCase) {
        try {
            ClassPathResource resource = new ClassPathResource("template/sendMessage.json");
            String cardContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            return cardContent
                .replace("${case}", currentCase)
                .replace("${text}", content);
        } catch (Exception e) {
            log.error("构建消息卡片失败", e);
            return null;
        }
    }

    @Override
    public String buildCreateSuccessCard(String url) {
        try {
            // 创建模板变量
            Map<String, Object> templateVariables = new HashMap<>();
            
            // 设置URL
            templateVariables.put("url", url);
            
            // 创建卡片模板数据
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("template_id", "AAqB3OW3kjMy0"); // 替换为实际的模板ID
            templateData.put("template_variable", templateVariables);
            
            // 创建完整的卡片内容
            Map<String, Object> cardContent = new HashMap<>();
            cardContent.put("type", "template");
            cardContent.put("data", templateData);
            
            // 转换为JSON字符串
            return JSON.toJSONString(cardContent);
        } catch (Exception e) {
            log.error("构建创建成功卡片失败", e);
            return null;
        }
    }

    @Override
    public String buildSelectSuccessCard(String caseName) {
        try {
            // 创建模板变量
            Map<String, Object> templateVariables = new HashMap<>();
            
            // 设置案件名称
            templateVariables.put("case", caseName);
            
            // 创建卡片模板数据
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("template_id", "AAqB3OKYhZkFC"); // 使用实际的模板ID
            templateData.put("template_variable", templateVariables);
            
            // 创建完整的卡片内容
            Map<String, Object> cardContent = new HashMap<>();
            cardContent.put("type", "template");
            cardContent.put("data", templateData);
            
            // 转换为JSON字符串
            return JSON.toJSONString(cardContent);
        } catch (Exception e) {
            log.error("构建选择成功卡片失败", e);
            return null;
        }
    }

    @Override
    public String buildCaseSummaryCard(String caseName, String content) {
        try {
            ClassPathResource resource = new ClassPathResource("template/caseSummary.json");
            String templateJson = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            // 处理Markdown内容中的特殊字符
            content = escapeMarkdown(content);
            
            // 使用JSON对象进行替换，而不是简单的字符串替换
            JSONObject cardJson = JSONObject.parseObject(templateJson);
            
            // 替换标题
            JSONObject header = cardJson.getJSONObject("header");
            JSONObject title = header.getJSONObject("title");
            title.put("content", "当前案件 " + caseName);
            
            // 替换Markdown内容
            JSONObject body = cardJson.getJSONObject("body");
            JSONArray elements = body.getJSONArray("elements");
            JSONObject markdown = elements.getJSONObject(1); // 第二个元素是markdown
            markdown.put("content", content);
            
            // 返回JSON字符串
            return cardJson.toString();
        } catch (Exception e) {
            log.error("构建案件总览卡片失败", e);
            return null;
        }
    }

    /**
     * 处理Markdown内容，使其适合飞书卡片
     */
    private String escapeMarkdown(String content) {
        if (content == null) {
            return "";
        }
        
        // 只转义引号，保留换行符
        return content.replace("\"", "\\\"");
    }

    @Override
    public String buildStreamingCard(String title) {
        try {
            // 读取JSON模板
            ClassPathResource resource = new ClassPathResource("template/json.json");
            String jsonTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            // 替换模板中的变量
            return jsonTemplate.replace("${case}", title);
        } catch (Exception e) {
            log.error("构建流式消息卡片失败", e);
            return null;
        }
    }

    @Override
    public String buildCardEntityContent(String cardId) {
        try {
            JSONObject cardData = new JSONObject();
            cardData.put("card_id", cardId);
            
            JSONObject content = new JSONObject();
            content.put("type", "card");
            content.put("data", cardData);
            
            return content.toString();
        } catch (Exception e) {
            log.error("构建卡片实体内容失败", e);
            return null;
        }
    }

    @Override
    public String buildLegalResearchCard(String caseName) {
        try {
            // 创建模板变量
            Map<String, Object> templateVariables = new HashMap<>();
            
            // 设置案件名称
            templateVariables.put("case", caseName);
            
            // 创建卡片模板数据
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("template_id", "AAqB02Ps6ef1E"); // 法律研究模板ID
            templateData.put("template_variable", templateVariables);
            
            // 创建完整的卡片内容
            Map<String, Object> cardContent = new HashMap<>();
            cardContent.put("type", "template");
            cardContent.put("data", templateData);
            
            // 转换为JSON字符串
            return JSON.toJSONString(cardContent);
        } catch (Exception e) {
            log.error("构建法律研究卡片失败", e);
            return null;
        }
    }
} 