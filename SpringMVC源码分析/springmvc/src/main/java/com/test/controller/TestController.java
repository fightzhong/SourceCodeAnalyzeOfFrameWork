package com.test.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;

@Controller
public class TestController {
    @RequestMapping( value = "/test1" )
    @ResponseBody
    public List<String> test1 () {
        return Arrays.asList( "a", "b" );
    }
}
