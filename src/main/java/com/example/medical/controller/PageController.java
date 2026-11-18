package com.example.medical.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() { return "redirect:/login"; }

    @GetMapping({"/login", "/login.html"})
    public String login() { return "login"; }                  // templates/login.html

    @GetMapping({"/records", "/records.html"})
    public String records() { return "records"; }              // templates/records.html

    @GetMapping({"/record-detail", "/record-detail.html"})
    public String recordDetail() { return "record-detail"; }   // templates/record-detail.html

    @GetMapping({"/access-grants", "/access-grants.html"})
    public String accessGrants() { return "access-grants"; }   // templates/access-grants.html
}