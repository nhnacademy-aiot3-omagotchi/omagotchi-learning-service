package site.omagotchi.learningservice.space.domain;

public final class SpaceValidationException extends IllegalArgumentException {

    private final Attribute attribute;

    SpaceValidationException(Attribute attribute, String message) {
        super(message);
        this.attribute = attribute;
    }

    public Attribute attribute() {
        return attribute;
    }

    public enum Attribute {
        NAME,
        TYPE,
        CAPACITY
    }
}
