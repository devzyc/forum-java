package pub.developers.forum.infrastructure.cache;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import pub.developers.forum.common.enums.CacheBizTypeEn;
import pub.developers.forum.common.enums.ErrorCodeEn;
import pub.developers.forum.common.exception.BizException;
import pub.developers.forum.common.support.SafesUtil;
import pub.developers.forum.domain.service.CacheService;
import pub.developers.forum.infrastructure.dal.dao.CacheDAO;
import pub.developers.forum.infrastructure.dal.dataobject.CacheDO;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Qiangqiang.Bian
 * @create 20/10/22
 * @desc
 **/
@Service
public class DbCacheServiceImpl implements CacheService {

    @Resource
    private CacheDAO cacheDAO;

    /**
     * 全部缓存
     */
    private static final Map<String, StringValue> ALL_CACHE = new ConcurrentHashMap<>();

    /**
     * 更新缓存
     */
    private static final Set<String> MODIFY_KEYS = new HashSet<>();

    /**
     * 新增缓存
     */
    private static final Set<String> NEW_KEYS = new HashSet<>();

    /**
     * 删除缓存
     */
    private static final Set<String> DELETE_KEYS = new HashSet<>();

    /**
     * 续期阈值：剩余时长不足原时长的这个比例时才真正续期并置脏
     */
    @Value("${custom-config.cache.renew-threshold-ratio:0.5}")
    private Double renewThresholdRatio;

    @Override
    public boolean set(CacheBizTypeEn bizType, String key, String value) {
        checkKey(key);

        String buildKey = buildKey(bizType, key);

        if (ALL_CACHE.containsKey(buildKey)) {
            MODIFY_KEYS.add(buildKey);
        } else {
            NEW_KEYS.add(buildKey);
        }
        ALL_CACHE.put(buildKey, new StringValue(bizType.getValue(), value));

        return Boolean.TRUE;
    }

    @Override
    public boolean setAndExpire(CacheBizTypeEn bizType, String key, String value, Long seconds) {
        checkKey(key);

        String buildKey = buildKey(bizType, key);

        if (ALL_CACHE.containsKey(buildKey)) {
            MODIFY_KEYS.add(buildKey);
        } else {
            NEW_KEYS.add(buildKey);
        }
        ALL_CACHE.put(buildKey, new StringValue(bizType.getValue(), value, seconds));

        return Boolean.TRUE;
    }

    @Override
    public String get(CacheBizTypeEn bizType, String key) {
        checkKey(key);

        StringValue stringValue = ALL_CACHE.get(buildKey(bizType, key));
        return stringValue == null ? null : stringValue.getValue();
    }

    @Override
    public Boolean exists(CacheBizTypeEn bizType, String key) {
        checkKey(key);

        return ALL_CACHE.containsKey(buildKey(bizType, key));
    }

    @Override
    public Boolean del(CacheBizTypeEn bizType, String key) {
        checkKey(key);

        String buildKey = buildKey(bizType, key);

        ALL_CACHE.remove(buildKey);
        DELETE_KEYS.add(buildKey);
        return Boolean.TRUE;
    }

    @Override
    public boolean renew(CacheBizTypeEn bizType, String key) {
        checkKey(key);

        String buildKey = buildKey(bizType, key);
        StringValue stringValue = ALL_CACHE.get(buildKey);
        // 条目不存在，或以 set() 存入的永不过期条目（无 seconds），都不续期
        if (null == stringValue || null == stringValue.getSeconds()) {
            return Boolean.FALSE;
        }

        long now = System.currentTimeMillis();
        long totalMillis = stringValue.getSeconds() * 1000L;
        // 已过期只是还没被定时任务扫掉，此时绝不复活，否则会制造"逻辑上已死、库里又清不掉"的幽灵会话
        if (now >= stringValue.getExpire()) {
            return Boolean.FALSE;
        }
        // 剩余时长还很充裕，不置脏，避免每个请求都刷一次 forum_cache 表
        if (stringValue.getExpire() - now > (long) (totalMillis * renewThresholdRatio)) {
            return Boolean.FALSE;
        }

        stringValue.setExpire(now + totalMillis);
        MODIFY_KEYS.add(buildKey);
        return Boolean.TRUE;
    }

    // -------------------------------- 缓存更新操作

    @PostConstruct
    public void postConstruct() {
        SafesUtil.ofList(cacheDAO.getAll()).forEach(cacheDO -> {
            StringValue stringValue = JSON.parseObject(cacheDO.getValue(), StringValue.class);
            ALL_CACHE.put(cacheDO.getKey(), stringValue);
        });
    }

    @PreDestroy
    public void preDestroy() {
        persistence();
    }

    /**
     * 秒 分 小时 月份中的日期 月份 星期中的日期 年份
     * 0 0/20 * * * * *  从 零分零秒开始 每隔20分钟 执行一次
     */
    @Scheduled(cron = "0/5 * * * * ? ")
    public void task() {
        SafesUtil.ofMap(ALL_CACHE).forEach((k, v) -> {
            if (v.getExpire() != -1L && System.currentTimeMillis() >= v.getExpire()) {
                ALL_CACHE.remove(k);
                DELETE_KEYS.add(k);
            }
        });
        persistence();
    }

    /**
     * 刷新缓存到磁盘
     */
    private void persistence() {
        // 删除 DELETE_KEYS
        if (!CollectionUtils.isEmpty(DELETE_KEYS)) {
            cacheDAO.batchDeleteByKeys(DELETE_KEYS);
            DELETE_KEYS.clear();
        }

        List<CacheDO> newCacheDOS = new ArrayList<>();
        ALL_CACHE.forEach((key, stringValue) ->{
            if (MODIFY_KEYS.contains(key)) {
                cacheDAO.updateByKey(key, JSON.toJSONString(stringValue));
            }
            if (NEW_KEYS.contains(key)) {
                CacheDO cacheDO = CacheDO.builder()
                        .key(key)
                        .value(JSON.toJSONString(stringValue))
                        .type(stringValue.getType())
                        .build();
                cacheDO.initBase();

                newCacheDOS.add(cacheDO);
            }
        });
        MODIFY_KEYS.clear();
        if (newCacheDOS.size() != 0) {
            newCacheDOS.forEach(cacheDO -> {
                try {
                    cacheDAO.insert(cacheDO);
                } catch (Exception e) {}
            });
        }
        NEW_KEYS.clear();
    }

    private void checkKey(String key) {
        if (StringUtils.isEmpty(key)) {
            throw new BizException(ErrorCodeEn.COMMON_CACHE_KEY_EMPTY);
        }
    }

    private String buildKey(CacheBizTypeEn bizType, String key) {
        return buildKey(bizType.getValue(), key);
    }

    private String buildKey(String bizType, String key) {
        return bizType + ":" + key;
    }

    @Data
    @NoArgsConstructor
    private static class StringValue {
        private String value;
        private Long expire;
        private String type;
        /**
         * 存入时的原始过期时长（单位：秒），供滑动续期算出新的过期时间；为 null 表示永不过期
         */
        private Long seconds;

        private StringValue(String type, String value, Long seconds) {
            this.type = type;
            this.value = value;
            this.seconds = seconds;
            this.expire = System.currentTimeMillis() + seconds * 1000;
        }

        private StringValue(String type, String value) {
            this.type = type;
            this.value = value;
            this.expire = -1L;
        }
    }
}
