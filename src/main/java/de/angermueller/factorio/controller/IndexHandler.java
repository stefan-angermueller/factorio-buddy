package de.angermueller.factorio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexHandler {

    @GetMapping("/")
    public String getIndex() {
        return "index";
    }

}
