package org.happyhai.springai.alibaba.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {

    @GetMapping("/translate")
    public String translatePage() {
        return "translate";
    }
}
