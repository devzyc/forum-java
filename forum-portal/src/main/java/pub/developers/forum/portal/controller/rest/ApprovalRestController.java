package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.*;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.service.ApprovalApiService;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2020/12/1
 * @desc
 **/
@RestController
@RequestMapping("/approval-rest")
public class ApprovalRestController {

    @Resource
    private ApprovalApiService approvalApiService;

    @PostMapping("/create/{postsId}")
    public ResultModel create(@PathVariable Long postsId) {
        return approvalApiService.create(postsId);
    }

    @PostMapping("/delete/{postsId}")
    public ResultModel delete(@PathVariable Long postsId) {
        return approvalApiService.delete(postsId);
    }
}
