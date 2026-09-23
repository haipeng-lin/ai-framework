package org.happyhai.springai.alibaba.voice.controller;

import org.happyhai.springai.alibaba.voice.service.AudioTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 页面视图与同步转录接口（参考 T05-voice-chat-robot）。
 */
@Controller
public class WebViewController {

    @Autowired
    private AudioTransactionService audioTransactionService;

    @GetMapping(path = "up")
    public String up() {
        return "up";
    }

    @GetMapping(path = "translate")
    public String translate() {
        return "translate";
    }

    /**
     * 音频转录为文字（单线程版本）。
     */
    @PostMapping(path = "translateAudio")
    @ResponseBody
    public String translateAudio(@RequestParam("file") MultipartFile file) throws IOException {
        return audioTransactionService.audioTransaction(file);
    }

    /**
     * 音频转录为文字（并行处理版本）。
     */
    @PostMapping(path = "translateAudioParallel")
    @ResponseBody
    public String translateAudioParallel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean useParallel) throws IOException {
        return audioTransactionService.audioTransactionParallel(file, useParallel);
    }
}
