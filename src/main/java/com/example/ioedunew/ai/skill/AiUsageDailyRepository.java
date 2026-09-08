package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiUsageDailyRepository extends JpaRepository<AiUsageDaily, Long> {
    Optional<AiUsageDaily> findByUserIdAndDay(Long userId, LocalDate day);

    List<AiUsageDaily> findByDayBetween(LocalDate from, LocalDate to);
}
