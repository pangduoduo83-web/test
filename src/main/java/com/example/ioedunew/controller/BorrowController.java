package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.BorrowDtos;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.service.BorrowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 借阅接口(学生端):申请、我的记录、撤销、申请归还。
 */
@RestController
@RequestMapping("/api/borrows")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping
    public ApiResponse<BorrowRequest> apply(@Valid @RequestBody BorrowDtos.ApplyRequest req,
                                            HttpServletRequest request) {
        return ApiResponse.ok(borrowService.apply(auth(request).getId(), req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<BorrowRequest>> mine(@RequestParam(required = false) String status,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(borrowService.myRequests(auth(request).getId(), status));
    }

    @GetMapping("/mine/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest request) {
        return ApiResponse.ok(borrowService.myStats(auth(request).getId()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        borrowService.cancel(auth(request).getId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/return")
    public ApiResponse<Void> requestReturn(@PathVariable Long id, HttpServletRequest request) {
        borrowService.requestReturn(auth(request).getId(), id);
        return ApiResponse.ok();
    }

    private AuthUser auth(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
    }
}
