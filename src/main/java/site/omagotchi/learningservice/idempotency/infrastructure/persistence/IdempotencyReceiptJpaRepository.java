package site.omagotchi.learningservice.idempotency.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.idempotency.domain.CommandReceipt;

import java.util.UUID;

public class IdempotencyReceiptJpaRepository implements JpaRepository<CommandReceipt, UUID> {
}
