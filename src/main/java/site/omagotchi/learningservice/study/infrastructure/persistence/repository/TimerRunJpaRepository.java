package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.domain.TimerRun;

import java.util.UUID;

public interface TimerRunJpaRepository extends JpaRepository<TimerRun, UUID> {
}
