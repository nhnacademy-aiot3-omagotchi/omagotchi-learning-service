package site.omagotchi.learningservice.space.domain;

public record SpaceAttributes(
        String name,
        SpaceType spaceType,
        Integer capacity
) {

    private static final int MAX_NAME_LENGTH = 50;

    public SpaceAttributes {
        name = validateName(name);
        spaceType = validateType(spaceType);
        capacity = validateCapacity(capacity);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new SpaceValidationException(
                    SpaceValidationException.Attribute.NAME,
                    "공간 이름은 필수입니다."
            );
        }

        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new SpaceValidationException(
                    SpaceValidationException.Attribute.NAME,
                    "공간 이름은 50자를 초과할 수 없습니다."
            );
        }

        return normalizedName;
    }

    private static SpaceType validateType(SpaceType spaceType) {
        if (spaceType == null) {
            throw new SpaceValidationException(
                    SpaceValidationException.Attribute.TYPE,
                    "공간 유형은 필수입니다."
            );
        }

        return spaceType;
    }

    private static Integer validateCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw new SpaceValidationException(
                    SpaceValidationException.Attribute.CAPACITY,
                    "공간 최대 인원은 양수여야 합니다."
            );
        }

        return capacity;
    }
}
