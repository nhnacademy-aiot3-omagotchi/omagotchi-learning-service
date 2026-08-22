package site.omagotchi.learningservice;

import org.springframework.boot.SpringApplication;

public class E2eLearningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(LearningServiceApplication::main)
                .with(E2eTestcontainersConfiguration.class)
                .run(args);
    }
}
