package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.TimerRunEntity;

public interface TimerRunRepository extends JpaRepository<TimerRunEntity, UUID> {
}
