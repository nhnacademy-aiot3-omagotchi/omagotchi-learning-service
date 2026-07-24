package site.omagotchi.learningservice.global.auth;

/**
 * 일반 사용자, 시스템 어드민
 */
public enum GlobalRole {
    USER,
    SYSTEM_ADMIN;

    public static GlobalRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return GlobalRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return USER;
        }
    }
}
