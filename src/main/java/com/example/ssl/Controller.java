package com.example.ssl;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
public class Controller {
    @GetMapping("/hello")
    public String hello() {
        return "Hello from Ryndych Dariia KP-31";
    }
}
