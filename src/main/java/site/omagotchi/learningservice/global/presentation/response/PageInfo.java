package site.omagotchi.learningservice.global.presentation.response;

public record PageInfo(
        int number,
        int size,
        long totalElements,
        int totalPages
) {
}
