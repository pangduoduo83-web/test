package com.example.ioedunew.repository;
import com.example.ioedunew.entity.ReviewJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ReviewJobRepository extends JpaRepository<ReviewJob, Long> {
    Optional<ReviewJob> findTopBySubmissionIdOrderByIdDesc(Long submissionId);
}
