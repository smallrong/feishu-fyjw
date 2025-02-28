package em.backend.handler;

/**
 * 事件处理器接口
 * @param <T> 事件类型
 * @param <R> 返回值类型
 */
public interface IEventHandler<T, R> {
    /**
     * 处理事件
     * @param event 事件对象
     * @return 处理结果
     */
    R handle(T event);
} 