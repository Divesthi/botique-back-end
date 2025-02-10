package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.service.StudentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom", produces = "application/json")
@CrossOrigin(origins="*")
public class TestController {

    @Autowired
    private StudentsService studentsService;

    @GetMapping("/hello")
    @ResponseBody
    public ResponseEntity<String> sayHello() {
        return new ResponseEntity<>("Welcome to Boutique Order Management System - Success1", HttpStatus.CREATED);
    }

    @GetMapping("/helloWithStatus")
    public ResponseEntity<List<String>> helloTest() {
        List<String> arrs = new ArrayList<>(2);
        arrs.add("Diya");
        arrs.add("Athira");
        return new ResponseEntity<>(arrs, HttpStatus.ACCEPTED);
    }

    @GetMapping("/students")
    public ResponseEntity<List<String>> getStudentNames() {
        return new ResponseEntity<>(studentsService.getStudentsName(), HttpStatus.OK);
    }
}
