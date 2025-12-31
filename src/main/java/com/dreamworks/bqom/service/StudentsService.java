package com.dreamworks.bqom.service;

import com.dreamworks.bqom.repository.StudentsRepository;
import com.dreamworks.bqom.repository.entity.Students;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StudentsService {
    @Autowired
    private StudentsRepository studentsRepository;

    public List<String> getStudentsName() {
        List<Students> students = studentsRepository.findAll();
        List<String> names = new ArrayList<>(1);
        if (students != null) {
            for (Students student: students) {
                names.add(student.getFirstName());
            }
        }
        return names;
    }
}
