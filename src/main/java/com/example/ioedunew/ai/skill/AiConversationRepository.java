package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {
    List<AiConversation> findTop50ByUserIdOrderByUpdatedAtDesc(Long userId);
}
