package com.team03.ticketmon._global.controller;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.service.TestUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test/upload")
public class TestUploadController {

    private final TestUploadService testUploadService;

    @PostMapping
    public ResponseEntity<UploadResponseDTO> uploadTest(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(testUploadService.uploadTestFile(file));
    }
}
