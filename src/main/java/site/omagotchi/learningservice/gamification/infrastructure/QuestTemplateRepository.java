package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.gamification.domain.QuestTemplate;
import site.omagotchi.learningservice.gamification.domain.QuestType;

import java.util.List;

/**
 * 퀘스트 템플릿
 */
public interface QuestTemplateRepository extends JpaRepository<QuestTemplate, Long> {

    List<QuestTemplate> findByActiveTrueOrderByDisplayOrderAsc();

    List<QuestTemplate> findByTypeAndActiveTrueOrderByDisplayOrderAsc(QuestType type);
}
