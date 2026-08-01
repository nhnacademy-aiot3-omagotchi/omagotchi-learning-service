package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.domain.TimerRun;

@Repository
@RequiredArgsConstructor
public class TimerRunJpaPersistence implements TimerRunRepository {

    private final TimerRunJpaRepository repository;

    @Override
    public TimerRun create(TimerRun timerRun) {
        return repository.save(timerRun);
    }

    @Override
    public void end(TimerRun timerRun) {
        repository.saveAndFlush(timerRun);
    }
}
