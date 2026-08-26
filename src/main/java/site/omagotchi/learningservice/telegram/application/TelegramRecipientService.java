package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramUserLinkRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 계정별 텔레그램 chatId 조회 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramRecipientService {

    private final TelegramUserLinkRepository userLinkRepository;

    public Optional<Long> findChatId(UUID userId){
        if(userId == null){
            return Optional.empty();
        }

        return userLinkRepository.findByUserIdAndDisconnectedAtIsNull(userId)
                .map(TelegramUserLink::getTelegramChatId);
    }
}
