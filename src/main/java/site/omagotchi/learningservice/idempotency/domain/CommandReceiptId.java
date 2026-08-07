package site.omagotchi.learningservice.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandReceiptId implements Serializable {

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    public static CommandReceiptId of(
            Long cohortMembershipId,
            UUID commandId
    ) {
        return new CommandReceiptId(cohortMembershipId, commandId);
    }
}