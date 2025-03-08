package em.backend.service;

import em.backend.pojo.CaseInfo;
import java.util.List;
import java.util.Map;

/**
 * 卡片模板服务接口
 */
public interface ICardTemplateService {
    /**
     * 构建创建案件卡片
     */
    String buildCreateCaseCard();

    /**
     * 构建选择案件卡片 (使用模板ID方式)
     * @param currentCase 当前案件
     * @param cases 案件列表
     * @param callbackName 回调方法名
     * @return 卡片内容JSON字符串
     */
    String buildSelectCaseCard(String currentCase, List<CaseInfo> cases, String callbackName);
    

    /**
     * 构建消息卡片
     * @param content 消息内容
     * @param currentCase 当前案件
     */
    String buildMessageCard(String content, String currentCase);

    /**
     * 构建创建成功卡片
     * @param url 文件夹URL
     */
    String buildCreateSuccessCard(String caseName,String url);

    /**
     * 构建选择成功卡片
     * @param caseName 案件名称
     */
    String buildSelectSuccessCard(String caseName);

    /**
     * 构建案件总览卡片
     * @param caseName 案件名称
     * @param content Markdown格式的内容
     */
    String buildCaseSummaryCard(String caseName, String content);

    /**
     * 构建流式消息卡片
     * @param title 卡片标题
     * @return 卡片JSON内容
     */
    String buildStreamingCard(String title);

    /**
     * 法律研究专用流式消息构建
     * @param title
     * @return
     */
    String buildStreamingCardV2(String title);

    /**
     * 构建卡片实体ID的内容
     * @param cardId 卡片实体ID
     * @return 卡片内容JSON字符串
     */
    String buildCardEntityContent(String cardId);

    /**
     * 构建法律研究卡片
     * @param caseName 案件名称
     * @return 卡片内容JSON字符串
     */
    String buildLegalResearchCard(String caseName);

    String buildErrorMessageCard(String content, String currentCase);

    /**
     * 构建模板卡片
     * @param templateId 模板ID
     * @param params 模板参数
     * @return 卡片内容JSON字符串
     */
    String buildTemplateCard(String templateId, Map<String, Object> params);
} 