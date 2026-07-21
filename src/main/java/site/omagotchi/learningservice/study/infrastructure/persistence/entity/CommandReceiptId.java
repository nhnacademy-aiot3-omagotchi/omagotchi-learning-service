package site.omagotchi.learningservice.study.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommandReceiptId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "command_id", nullable = false, updatable = false)
    private UUID commandId;

    public CommandReceiptId(Long cohortMembershipId, UUID commandId) {
        this.cohortMembershipId = cohortMembershipId;
        this.commandId = commandId;
    }
}
