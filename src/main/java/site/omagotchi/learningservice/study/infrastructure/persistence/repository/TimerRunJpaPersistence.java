package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.domain.TimerRun;

@Repository
@RequiredArgsConstructor
public class TimerRunJpaPersistence implements TimerRunRepository {

    private final TimerRunJpaRepository timerRunJpaRepository;

    @Override
    public TimerRun create(TimerRun timerRun) {
        return timerRunJpaRepository.save(timerRun);
    }

    @Override
    public void end(TimerRun timerRun) {
        timerRunJpaRepository.saveAndFlush(timerRun);
    }
}
