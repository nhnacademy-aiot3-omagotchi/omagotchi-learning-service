package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.CommandReceiptEntity;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.CommandReceiptId;

public interface CommandReceiptRepository extends JpaRepository<CommandReceiptEntity, CommandReceiptId> {
}
