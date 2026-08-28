package site.omagotchi.learningservice.telegram.application.command;

import java.util.Objects;

public enum BotCommand {
    START("/start"),
    STOP("/stop"),
    RESUME("/resume"),
    DISCONNECT("/disconnect"),
    STATUS("/status"),
    HELP("/help"),
    UNKNOWN("");

    private final String keyword;

    BotCommand(String keyword) {
        this.keyword = keyword;
    }

    public static BotCommand of(String text) {
        if (Objects.isNull(text) || text.isBlank()) {
            return UNKNOWN;
        }

        String head = text.trim().split("\\s+", 2)[0].split("@", 2)[0];

        for (BotCommand command : values()) {
            if (command != UNKNOWN && command.keyword.equals(head)) {
                return command;
            }
        }

        return UNKNOWN;
    }
}
