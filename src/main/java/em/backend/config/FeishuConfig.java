package em.backend.config;

import com.lark.oapi.Client;
import com.lark.oapi.core.cache.LocalCache;
import com.lark.oapi.core.httpclient.OkHttpTransport;
import com.lark.oapi.okhttp.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 飞书配置类
 */
@Configuration
public class FeishuConfig {

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    /**
     * 创建飞书 API 客户端
     * 用于调用飞书的 OpenAPI 接口
     */
    @Bean
    public Client feishuClient() {
        // 创建 OkHttpClient 并配置超时时间
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .readTimeout(90, TimeUnit.MINUTES)    // 读取超时时间
                .writeTimeout(90, TimeUnit.MINUTES)   // 写入超时时间
                .connectTimeout(90, TimeUnit.MINUTES) // 连接超时时间
                .callTimeout(90, TimeUnit.MINUTES)    // 整体请求超时时间
                .build();

        // 创建飞书客户端
        return Client.newBuilder(appId, appSecret)
                .httpTransport(new OkHttpTransport(okHttpClient)) // 使用自定义的 OkHttpClient
                .tokenCache(LocalCache.getInstance())  // 使用默认的本地缓存实现
                .logReqAtDebug(true)  // 开启调试日志
                .build();
    }
} 