package pub.developers.forum.facade.aspect;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import pub.developers.forum.app.support.LoginUserContext;
import pub.developers.forum.common.constant.Constant;
import pub.developers.forum.common.enums.CacheBizTypeEn;
import pub.developers.forum.domain.entity.User;
import pub.developers.forum.domain.service.CacheService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/31
 * @desc
 **/
@Slf4j
@Component
@Aspect
public class LoginUserAspect {

    @Resource
    private HttpServletRequest httpServletRequest;

    @Resource
    private CacheService cacheService;

    @Around("execution(* pub.developers.forum.api..*.*(..))")
    public Object invoke(ProceedingJoinPoint pjp) throws Throwable {
        String token = httpServletRequest.getHeader(Constant.REQUEST_HEADER_TOKEN_KEY);

        if (ObjectUtils.isEmpty(token)) {
            Object tokenObj = httpServletRequest.getAttribute(Constant.REQUEST_HEADER_TOKEN_KEY);
            if (ObjectUtils.isEmpty(tokenObj)) {
                return pjp.proceed();
            }

            token = String.valueOf(tokenObj);
        }

        String cacheString = cacheService.get(CacheBizTypeEn.USER_LOGIN_TOKEN, token);
        if (ObjectUtils.isEmpty(cacheString)) {
            return pjp.proceed();
        }

        User user = JSON.parseObject(cacheString, User.class);
        LoginUserContext.setToken(token);
        LoginUserContext.setUser(user);

        // 滑动窗口续期：会话在距最后一次成功使用满一个窗口时长后才失效。
        // token=>user 与 uid=>token 两条 key 必须成对续，只续一条会让 deleteLoginUser 找不到旧 token，会话只增不减
        cacheService.renew(CacheBizTypeEn.USER_LOGIN_TOKEN, token);
        if (!ObjectUtils.isEmpty(user.getId())) {
            cacheService.renew(CacheBizTypeEn.USER_LOGIN_TOKEN, String.valueOf(user.getId()));
        }

        try {
            return pjp.proceed();
        } finally {
            LoginUserContext.removeAll();
        }
    }


}
