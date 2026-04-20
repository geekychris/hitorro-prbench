package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BotRepository extends JpaRepository<Bot, Long> {
    Optional<Bot> findByName(String name);
}
