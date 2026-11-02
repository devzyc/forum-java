package pub.developers.forum.portal.controller.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pub.developers.forum.api.model.ResultModel;
import pub.developers.forum.api.service.MessageApiService;

import javax.annotation.Resource;

/**
 * @author Qiangqiang.Bian
 * @create 2020/12/5
 * @desc
 **/
@RestController
@RequestMapping("/message-rest")
public class MessageRestController {

    @Resource
    private MessageApiService messageApiService;

    @PostMapping("/mark-is-read/{id}")
    public ResultModel delete(@PathVariable("id") Long id) {
        return messageApiService.markIsRead(id);
    }


}
