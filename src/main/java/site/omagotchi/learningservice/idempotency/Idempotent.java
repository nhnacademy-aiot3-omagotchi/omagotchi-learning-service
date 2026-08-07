package site.omagotchi.learningservice.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * 영수증에 저장되는 안정적인 명령 식별자.
     * 
     * 메서드명을 그대로 사용하지 않는다.
     * 메서드 리팩터링 후에도 기존 영수증을 해석할 수 있어야 한다.
     */
    String operation();
}
/*
사용 예:
@Idempotent(operation = "STUDY_RECORD_CREATE")
public CreateStudyRecordResult create(...) {
}
 */