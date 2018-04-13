package com.crypterac.backend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HealthController {

    @RequestMapping("/ping")
    public @ResponseBody String ping() {
        return "pong";
    }
}
