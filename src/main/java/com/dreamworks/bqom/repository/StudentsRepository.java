package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.Students;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentsRepository extends JpaRepository<Students, Long> { }