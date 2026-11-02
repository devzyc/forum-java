package pub.developers.forum.portal.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.response.user.UserInfoResponse;
import pub.developers.forum.api.service.UserApiService;
import pub.developers.forum.common.constant.Constant;
import pub.developers.forum.common.support.RequestContext;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/29
 * @desc
 **/
@Slf4j
@Component
public class GlobalViewInterceptor extends HandlerInterceptorAdapter {

    @Resource
    private UserApiService userApiService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestContext.init();

        // 登录凭证统一在这里注入 request attribute，各 Controller 不再逐接口手写 setAttribute
        String token = WebUtil.resolveToken(request);
        request.setAttribute(Constant.REQUEST_HEADER_TOKEN_KEY, token);
        if (ObjectUtils.isEmpty(token)) {
            return true;
        }

        ResultModel<UserInfoResponse> resultModel = userApiService.info(token);
        if (resultModel.getSuccess() && !ObjectUtils.isEmpty(resultModel.getData())) {
            WebContext.setCurrentSid(token);
            WebContext.setCurrentUser(resultModel.getData());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理必须放在 afterCompletion：handler 抛异常时 Spring 不会调 postHandle，
        // 留在 postHandle 里会让 ThreadLocal 残留，Tomcat 复用线程时下一个请求可能读到上一个用户的身份
        WebContext.removeAll();
        RequestContext.removeAll();
    }

}
