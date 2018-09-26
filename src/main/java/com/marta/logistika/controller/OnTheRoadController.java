package com.marta.logistika.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logiweb")
public class OnTheRoadController {

    @GetMapping
    public String home(){
        return "drivers/view";
    }
}