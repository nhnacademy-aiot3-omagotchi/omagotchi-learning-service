package site.omagotchi.learningservice.study.application.port;

import site.omagotchi.learningservice.study.domain.TimerRun;

public interface TimerRunRepository {
    TimerRun create(TimerRun timerRun);

    void end(TimerRun timerRun);
}
