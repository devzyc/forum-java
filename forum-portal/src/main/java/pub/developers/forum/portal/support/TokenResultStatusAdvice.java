package pub.developers.forum.portal.support;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.common.enums.ErrorCodeEn;

/**
 * 把业务错误码翻译成 HTTP 状态码，让客户端可以用 401 / 403 统一识别会话失效
 * <p>
 * 之所以放在 portal 层基于"最终响应体"判断，而不是在 facade 切面里 setStatus：
 * GlobalViewInterceptor 对每个带 token 的请求都会调一次 userApiService.info(token)，
 * 游客访问业务接口时这次调用会失败，若在那里改状态码，会把一个内容正常的 200 响应污染成 401。
 *
 * @author Qiangqiang.Bian
 * @create 2020/11/2
 * @desc
 **/
@RestControllerAdvice
public class TokenResultStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ResultModel.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ResultModel)) {
            return body;
        }

        Integer code = ((ResultModel<?>) body).getCode();
        if (null == code) {
            return body;
        }

        if (ErrorCodeEn.USER_NOT_LOGIN.getCode().equals(code)
                || ErrorCodeEn.USER_TOKEN_INVALID.getCode().equals(code)) {
            // 会话不存在或已过期：客户端清掉本地 token 并跳登录页
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        } else if (ErrorCodeEn.COMMON_TOKEN_NO_PERMISSION.getCode().equals(code)
                || ErrorCodeEn.NO_PERMISSION.getCode().equals(code)) {
            // 身份有效但角色不够：只提示，不应踢登录态
            response.setStatusCode(HttpStatus.FORBIDDEN);
        }

        return body;
    }
}
