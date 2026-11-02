package pub.developers.forum.app.manager;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ObjectUtils;
import pub.developers.forum.api.request.user.UserBaseLoginRequest;
import pub.developers.forum.common.enums.CacheBizTypeEn;
import pub.developers.forum.common.support.EventBus;
import pub.developers.forum.common.support.StringUtil;
import pub.developers.forum.domain.entity.OptLog;
import pub.developers.forum.domain.entity.User;
import pub.developers.forum.domain.repository.UserRepository;
import pub.developers.forum.domain.service.CacheService;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2021/5/15
 * @desc
 **/
public class AbstractLoginManager {

    @Resource
    UserRepository userRepository;

    @Resource
    CacheService cacheService;

    /**
     * 用户登录凭证 token 的过期时长（单位：秒），即滑动窗口的窗口大小
     */
    @Value("${custom-config.token.expire-seconds:86400}")
    private Long tokenExpireSeconds;

    protected String login(User user, UserBaseLoginRequest baseLoginRequest) {
        // 缓存登录凭证 token =》 user
        String token = StringUtil.generateUUID();
        cacheLoginUser(token, user);

        // 触发保存操作日志事件（入参里带明文密码，必须走掩码序列化）
        EventBus.emit(EventBus.Topic.USER_LOGIN, OptLog.createUserLoginRecordLog(user.getId(), StringUtil.toJSONString(baseLoginRequest)));

        return token;
    }

    protected void cacheLoginUser(String token, User user) {
        // 删除之前登录缓存
        deleteLoginUser(user.getId());

        // 重新保存缓存：两条 key 必须成对写入、成对续期，否则 deleteLoginUser 会找不到旧 token 而残留会话
        cacheService.setAndExpire(CacheBizTypeEn.USER_LOGIN_TOKEN
                , String.valueOf(user.getId()), token, tokenExpireSeconds);
        cacheService.setAndExpire(CacheBizTypeEn.USER_LOGIN_TOKEN
                , token, JSON.toJSONString(user), tokenExpireSeconds);
    }

    protected void deleteLoginUser(Long userId) {
        // 删除之前登录缓存
        String oldToken = cacheService.get(CacheBizTypeEn.USER_LOGIN_TOKEN, String.valueOf(userId));
        if (!ObjectUtils.isEmpty(oldToken)) {
            cacheService.del(CacheBizTypeEn.USER_LOGIN_TOKEN, String.valueOf(userId));
            cacheService.del(CacheBizTypeEn.USER_LOGIN_TOKEN, oldToken);
        }
    }
}
