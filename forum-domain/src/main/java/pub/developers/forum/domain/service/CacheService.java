package pub.developers.forum.domain.service;

import pub.developers.forum.common.enums.CacheBizTypeEn;

/**
 * @author Qiangqiang.Bian
 * @create 20/7/23
 * @desc
 **/
public interface CacheService {

    /**
     * 存储
     * @param key
     * @param value
     * @return
     */
    boolean set(CacheBizTypeEn bizType, String key, String value);

    /**
     * 存储并设置超时时长(单位:秒)
     * @param key
     * @param value
     * @param seconds
     * @return
     */
    boolean setAndExpire(CacheBizTypeEn bizType, String key, String value, Long seconds);

    /**
     * 获取值
     * @param key
     * @return
     */
    String get(CacheBizTypeEn bizType, String key);

    /**
     * 判断是否存在
     * @param key
     * @return
     */
    Boolean exists(CacheBizTypeEn bizType, String key);

    /**
     * 按存入时的时长续期（滑动窗口）
     * <p>
     * 仅在条目存在、且剩余时长不足原时长一半时才真正续期，避免每个请求都触发落库。
     * 不存在或已过期的条目不会被复活，永不过期的条目不做处理。
     *
     * @param key
     * @return 是否实际发生了续期
     */
    boolean renew(CacheBizTypeEn bizType, String key);

    /**
     * 删除
     * @param key
     * @return
     */
    Boolean del(CacheBizTypeEn bizType, String key);

}
