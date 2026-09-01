package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.application.result.SelectableLabView;

public record SelectableLabResponse(
        Long spaceId,
        String name,
        Integer capacity,
        long reservedCount
) {

    public static SelectableLabResponse from(SelectableLabView view) {
        return new SelectableLabResponse(
                view.spaceId(),
                view.name(),
                view.capacity(),
                view.reservedCount()
        );
    }
}
