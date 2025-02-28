package em.backend.common;

/**
 * 返回状态码
 */
public interface ResultCode {
    
    // 成功
    Integer SUCCESS = 200;
    
    // 失败
    Integer ERROR = 500;
    
    // 未授权
    Integer UNAUTHORIZED = 401;
    
    // 禁止访问
    Integer FORBIDDEN = 403;
    
    // 未找到
    Integer NOT_FOUND = 404;
    
    // 参数错误
    Integer PARAM_ERROR = 400;
} 