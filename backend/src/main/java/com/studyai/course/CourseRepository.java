package com.studyai.course;

import com.studyai.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserOrderByCreatedAtDesc(User user);

    List<Course> findByUserOrderByCreatedAtAscIdAsc(User user);

    Optional<Course> findByIdAndUser(Long id, User user);

    boolean existsByIdAndUser(Long id, User user);
}
