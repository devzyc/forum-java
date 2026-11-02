package pub.developers.forum.facade.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import pub.developers.forum.common.enums.ErrorCodeEn;
import pub.developers.forum.common.exception.BizException;
import pub.developers.forum.common.support.LogUtil;
import pub.developers.forum.common.support.StringUtil;
import pub.developers.forum.facade.support.ResultModelUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/28
 * @desc
 **/
@Slf4j
@Component
@Aspect
public class ExceptionHandlerAndLogAspect {

    /**
     * 会话失效的错误码：客户端本地存着过期 token 时，每个请求都会命中一次（拦截器里还会多一次），
     * 属于正常业务分支而非故障，不能按完整请求日志刷盘
     */
    private static final List<Integer> SESSION_INVALID_CODES = Arrays.asList(
            ErrorCodeEn.USER_NOT_LOGIN.getCode(),
            ErrorCodeEn.USER_TOKEN_INVALID.getCode());

    @Resource
    private HttpServletRequest httpServletRequest;

    @Around("execution(* pub.developers.forum.api..*.*(..))")
    public Object invoke(ProceedingJoinPoint pjp) throws Throwable {
        Long start = System.currentTimeMillis();
        Object result;
        Throwable throwable = null;
        Boolean invokeSuccess = true;
        Integer bizCode = null;

        try {
            result = pjp.proceed();
        } catch (BizException bizException) {
            result = ResultModelUtil.fail(bizException.getCode(), bizException.getMessage());
            invokeSuccess = false;
            bizCode = bizException.getCode();
        } catch (Exception e) {
            result = ResultModelUtil.fail(ErrorCodeEn.SYSTEM_ERROR);
            invokeSuccess = false;
            throwable = e;
        }

        if (null != bizCode && SESSION_INVALID_CODES.contains(bizCode)) {
            LogUtil.info(log, "session invalid. api={}#{}. code={}. cost={}ms"
                    , pjp.getSourceLocation().getWithinType().getName()
                    , pjp.getSignature().getName()
                    , bizCode
                    , System.currentTimeMillis() - start);

            return result;
        }

        logRequestInfo(invokeSuccess, pjp, start, result, throwable);

        return result;
    }

    private void logRequestInfo(Boolean invokeSuccess, ProceedingJoinPoint pjp, Long start, Object result, Throwable e) {
        Long cost = System.currentTimeMillis() - start;
        if (null == e) {
            LogUtil.info(log, "{}, ip={}. api={}#{}. cost={}ms. params={}. result={}"
                    , invokeSuccess ? "success" : "fail"
                    , httpServletRequest.getRemoteAddr()
                    , pjp.getSourceLocation().getWithinType().getName()
                    , pjp.getSignature().getName()
                    , cost
                    , StringUtil.toJSONString(pjp.getArgs())
                    , (null == result ? "" : StringUtil.toJSONString(result)));
            return;
        }

        LogUtil.info(log, e, "fail, ip={}. api={}#{}. cost={}ms. params={}. result={}. exception={}"
                , httpServletRequest.getRemoteAddr()
                , pjp.getSourceLocation().getWithinType().getName()
                , pjp.getSignature().getName()
                , cost
                , StringUtil.toJSONString(pjp.getArgs())
                , (null == result ? "" : StringUtil.toJSONString(result))
                , e.getClass().getName() + ": " + e.getMessage());
    }


}
