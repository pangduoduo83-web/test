package com.example.ioedunew.controller;
import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.service.DirectUploadService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.Map;

@RestController
@RequestMapping("/api/upload/direct")
public class DirectUploadController {
    private final DirectUploadService service;
    public DirectUploadController(DirectUploadService service) { this.service=service; }
    @PostMapping("/initiate")
    public ApiResponse<Map<String,Object>> initiate(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user,
            @Valid @RequestBody Request body, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.initiate(user,body.getKind(),body.getName(),body.getSize()));
    }
    @PostMapping("/{id}/complete")
    public ApiResponse<Map<String,String>> complete(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user, @PathVariable String id) {
        return ApiResponse.ok(service.complete(user,id));
    }
    @Data public static class Request {
        @NotBlank @Size(max=16) private String kind;
        @NotBlank @Size(max=255) private String name;
        @Min(1) @Max(524288000) private long size;
    }
}
