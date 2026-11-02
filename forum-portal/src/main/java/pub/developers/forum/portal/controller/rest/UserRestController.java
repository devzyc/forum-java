package pub.developers.forum.portal.controller.rest;

import com.google.common.collect.Sets;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pub.developers.forum.api.model.PageRequestModel;
import pub.developers.forum.api.model.PageResponseModel;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.request.article.ArticleAuthorPageRequest;
import pub.developers.forum.api.request.file.FileUploadImgRequest;
import pub.developers.forum.api.request.user.*;
import pub.developers.forum.api.response.article.ArticleUserPageResponse;
import pub.developers.forum.api.response.user.UserInfoResponse;
import pub.developers.forum.api.response.user.UserPageResponse;
import pub.developers.forum.api.service.ArticleApiService;
import pub.developers.forum.api.service.FileApiService;
import pub.developers.forum.api.service.UserApiService;
import pub.developers.forum.common.enums.ErrorCodeEn;
import pub.developers.forum.common.support.CheckUtil;
import pub.developers.forum.portal.support.WebUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/**
 * @author Qiangqiang.Bian
 * @create 2020/10/29
 * @desc
 **/
@RestController
@RequestMapping("/user-rest")
public class UserRestController {

    @Resource
    private UserApiService userApiService;

    @Resource
    private ArticleApiService articleApiService;

    @Resource
    private FileApiService fileApiService;

    // .css;.js;.png;.jpeg;.jpg;.woff2;.html;.ico;.gif;.bmp;.svg;.woff;.map
    private static final Set<String> ALLOW_TYPES = Sets.newHashSet("png", "jpeg", "jpg", "ico", "gif", "bmp", "svg");

    @PostMapping("/register")
    public ResultModel<String> register(@RequestBody UserRegisterRequest request, HttpServletRequest servletRequest) {
        request.setIp(WebUtil.requestIp(servletRequest));
        request.setUa(WebUtil.requestUa(servletRequest));

        return userApiService.register(request);
    }

    @PostMapping("/login")
    public ResultModel<String> login(@RequestBody UserEmailLoginRequest request, HttpServletRequest servletRequest) {
        request.setIp(WebUtil.requestIp(servletRequest));
        request.setUa(WebUtil.requestUa(servletRequest));

        return userApiService.emailLogin(request);
    }

    /**
     * 退出登录
     * <p>
     * 服务端删除 token=>user 与 uid=>token 两条缓存，客户端自行清掉本地 storage。
     */
    @PostMapping("/logout")
    public ResultModel logout(HttpServletRequest request) {
        String token = WebUtil.resolveToken(request);
        if (ObjectUtils.isEmpty(token)) {
            // 已经没有登录凭证，登出按幂等处理，免得前端为"清本地"还要分情况判断
            return WebUtil.success(null);
        }

        UserTokenLogoutRequest logoutRequest = new UserTokenLogoutRequest();
        logoutRequest.setToken(token);

        return userApiService.logout(logoutRequest);
    }

    @PostMapping("/update-info")
    public ResultModel updateInfo(@RequestBody UserUpdateInfoRequest updateInfoRequest) {
        return userApiService.updateInfo(updateInfoRequest);
    }

    @PostMapping("/update-headimg")
    public ResultModel ResultModelupdateHeadImg(MultipartFile file) throws IOException {
        //获取原始文件名
        String originalFilename = file.getOriginalFilename();
        ResultModel<String> linkFilename = updateHeadimg(file, originalFilename);
        return userApiService.updateHeadImg(linkFilename.getData());
    }

    private ResultModel<String> updateHeadimg(MultipartFile file, String originalFilename) {
        String fileType = file.getContentType();
        boolean isAllowType = false;
        for (String allowType : ALLOW_TYPES) {
            if (fileType != null && fileType.contains(allowType)) {
                isAllowType = true;
                break;
            }
        }
        CheckUtil.isFalse(isAllowType, ErrorCodeEn.FILE_UPLOAD_NOT_SUPPORT_IMG_TYPE);

        FileUploadImgRequest uploadImgRequest = null;
        try {
            uploadImgRequest = FileUploadImgRequest.builder()
                    .base64(file.getBytes())
                    .fileName(originalFilename)
                    .build();
        } catch (Exception e) {
            CheckUtil.isTrue(true, ErrorCodeEn.FILE_UPLOAD_FAIL);
        }

        return fileApiService.uploadImg(uploadImgRequest);
    }

    @PostMapping("/update-pwd")
    public ResultModel updatePwd(@RequestBody UserUpdatePwdRequest updatePwdRequest) {
        return userApiService.updatePwd(updatePwdRequest);
    }

    @PostMapping("/follow/{followed}")
    public ResultModel follow(@PathVariable("followed") Long followed) {
        return userApiService.follow(followed);
    }

    @PostMapping("/cancel-follow/{followed}")
    public ResultModel cancelFollow(@PathVariable("followed") Long followed) {
        return userApiService.cancelFollow(followed);
    }

    /**
     * 获取当前登录用户信息，小程序用作会话探活接口
     * <p>
     * 未登录 / token 已过期都会因 USER_NOT_LOGIN 返回 401，前端据此跳登录页
     */
    @GetMapping("/current")
    public ResultModel<UserInfoResponse> current() {
        return userApiService.current();
    }

    /**
     * 获取指定用户的文章列表 REST API
     * 通过可选参数控制是否分页
     * 当 pageNo 和 pageSize 都未提供时，返回所有文章
     * 当提供 pageNo 和 pageSize 时，返回分页结果
     */
    @GetMapping("/articles/{uid}")
    public ResultModel<?> getUserArticles(
            @PathVariable("uid") Long uid,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        PageRequestModel<ArticleAuthorPageRequest> pageRequestModel = new PageRequestModel<>();
        pageRequestModel.setPageNo(ObjectUtils.isEmpty(pageNo) ? 1 : pageNo);
        pageRequestModel.setPageSize(ObjectUtils.isEmpty(pageSize) ? 9999 : pageSize);
        pageRequestModel.setFilter(ArticleAuthorPageRequest.builder()
                .userId(uid)
                .build());

        ResultModel<PageResponseModel<ArticleUserPageResponse>> result = articleApiService.authorPage(pageRequestModel);

        // 未分页时只返回列表数据
        if (ObjectUtils.isEmpty(pageNo) || ObjectUtils.isEmpty(pageSize)) {
            if (result.getSuccess() && result.getData() != null) {
                return WebUtil.success(result.getData().getList());
            }
            return WebUtil.success(new ArrayList<>());
        }

        return result;
    }

    /**
     * 获取指定用户的关注列表 REST API
     * 通过可选参数控制是否分页
     * 当 pageNo 和 pageSize 都未提供时，返回所有关注的人
     * 当提供 pageNo 和 pageSize 时，返回分页结果
     */
    @GetMapping("/followers/{uid}")
    public ResultModel<?> getFollowers(
            @PathVariable("uid") Long uid,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        PageRequestModel<Long> pageRequestModel = new PageRequestModel<>();
        pageRequestModel.setPageNo(ObjectUtils.isEmpty(pageNo) ? 1 : pageNo);
        pageRequestModel.setPageSize(ObjectUtils.isEmpty(pageSize) ? 9999 : pageSize);
        pageRequestModel.setFilter(uid);

        ResultModel<PageResponseModel<UserPageResponse>> result = userApiService.pageFollower(pageRequestModel);

        // 未分页时只返回列表数据
        if (ObjectUtils.isEmpty(pageNo) || ObjectUtils.isEmpty(pageSize)) {
            if (result.getSuccess() && result.getData() != null) {
                return WebUtil.success(result.getData().getList());
            }
            return WebUtil.success(new ArrayList<>());
        }

        return result;
    }
}
