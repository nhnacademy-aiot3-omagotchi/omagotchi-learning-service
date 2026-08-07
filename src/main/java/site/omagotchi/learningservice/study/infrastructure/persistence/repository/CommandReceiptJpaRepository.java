package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.idempotency.domain.CommandReceipt;
import site.omagotchi.learningservice.idempotency.domain.CommandReceiptId;

public interface CommandReceiptJpaRepository extends JpaRepository<CommandReceipt, CommandReceiptId> {
}
