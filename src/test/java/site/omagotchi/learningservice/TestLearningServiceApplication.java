package site.omagotchi.learningservice;

import org.springframework.boot.SpringApplication;

public class TestLearningServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(LearningServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
