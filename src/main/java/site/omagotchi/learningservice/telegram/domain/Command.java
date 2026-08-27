package site.omagotchi.learningservice.telegram.domain;

import java.util.Objects;

public enum Command {
    START("/start"),
    STOP("/stop"),
    RESUME("/resume"),
    DISCONNECT("/disconnect"),
    STATUS("/status"),
    HELP("/help"),
    UNKNOWN("");

    private final String keyword;

    Command(String keyword) {
        this.keyword = keyword;
    }

    public static Command of(String text) {
        if (Objects.isNull(text) || text.isBlank()) {
            return UNKNOWN;
        }

        String head = text.trim().split("\\s+")[0].split("@")[0];

        for (Command command : values()) {
            if (command != UNKNOWN && command.keyword.equals(head)) {
                return command;
            }
        }

        return UNKNOWN;
    }
}
