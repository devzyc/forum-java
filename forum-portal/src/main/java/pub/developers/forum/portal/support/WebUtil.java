package pub.developers.forum.portal.support;

import org.springframework.util.ObjectUtils;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.common.constant.Constant;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/25
 * @desc
 **/
public class WebUtil {

    /**
     * 取登录凭证 token
     * <p>
     * 只读请求头，请求头取不到时降级读 URL 参数（给无法自定义请求头的场景留口子，如图片直链）。
     * 不再读 Cookie：小程序读不到 Set-Cookie，也无法手写 Cookie 头，会话持久化统一由客户端 storage 存 token 完成。
     *
     * @param request
     * @return 没有登录凭证时返回 null
     */
    public static String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(Constant.REQUEST_HEADER_TOKEN_KEY);
        if (!ObjectUtils.isEmpty(token)) {
            return token;
        }

        String queryToken = request.getParameter(Constant.REQUEST_QUERY_TOKEN_KEY);
        if (!ObjectUtils.isEmpty(queryToken)) {
            return queryToken;
        }

        return null;
    }

    public static String requestIp(HttpServletRequest request) {
        String ret = request.getHeader("X-forwarded-for");
        if(ObjectUtils.isEmpty(ret)) {
            ret = request.getHeader("X-Real-IP");
        }

        return ObjectUtils.isEmpty(ret) ? request.getRemoteAddr() : ret.split(",")[0];
    }

    public static String requestUa(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (ObjectUtils.isEmpty(value)) {
            return "";
        }

        return value;
    }

    public static <T> ResultModel<T> success(T data) {
        ResultModel<T> resultModel = new ResultModel<T>();
        resultModel.setData(data);

        return resultModel;
    }
}
