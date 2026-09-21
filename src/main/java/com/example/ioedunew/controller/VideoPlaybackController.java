package com.example.ioedunew.controller;
import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.service.VideoPlaybackService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController
public class VideoPlaybackController {
    private final VideoPlaybackService service;
    public VideoPlaybackController(VideoPlaybackService service) { this.service=service; }
    @GetMapping("/api/media/playback")
    public ApiResponse<Map<String,Object>> playback(@RequestParam String url,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return ApiResponse.ok(service.playback(url));
    }
    @GetMapping("/api/public/media/hls/{month}/{file}/{quality}.m3u8")
    public ResponseEntity<String> playlist(@PathVariable String month,@PathVariable String file,@PathVariable String quality,
            @RequestParam long expires,@RequestParam String token) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl;charset=UTF-8"))
                .body(service.playlist(month+"/"+file,quality,expires,token));
    }
    // Players must see a real error status; a 200 JSON error is not an HLS playlist.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> failed(BusinessException error) {
        int status=error.getCode();
        if (status<400 || status>599) status=500;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.error(error.getCode(),error.getMessage()));
    }
}
