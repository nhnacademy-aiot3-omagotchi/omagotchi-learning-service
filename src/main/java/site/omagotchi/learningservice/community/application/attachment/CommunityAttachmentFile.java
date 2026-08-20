package site.omagotchi.learningservice.community.application.attachment;

import java.io.IOException;
import java.io.InputStream;

public record CommunityAttachmentFile(
        String originalFileName,
        String contentType,
        long sizeBytes,
        int displayOrder,
        StreamSource streamSource
) {

    public InputStream openStream() throws IOException {
        return streamSource.openStream();
    }

    @FunctionalInterface
    public interface StreamSource {
        InputStream openStream() throws IOException;
    }
}
