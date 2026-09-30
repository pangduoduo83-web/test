package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Feedback;
import com.example.ioedunew.service.FeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedbacks")
    public ApiResponse<Feedback> create(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user,
                                        @Valid @RequestBody MiscDtos.FeedbackCreateRequest request) {
        return ApiResponse.ok(feedbackService.create(user, request));
    }

    @GetMapping("/feedbacks/mine")
    public ApiResponse<List<Feedback>> mine(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(feedbackService.mine(user.getId()));
    }

    @GetMapping("/admin/feedbacks")
    public ApiResponse<List<Feedback>> adminList(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(feedbackService.adminList(status, category, keyword));
    }

    @PutMapping("/admin/feedbacks/{id}")
    public ApiResponse<Feedback> update(@PathVariable Long id,
                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser admin,
                                        @Valid @RequestBody MiscDtos.FeedbackUpdateRequest request) {
        return ApiResponse.ok(feedbackService.update(id, admin, request));
    }
}
