package site.omagotchi.learningservice.community.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.global.exception.BusinessException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("커뮤니티 첨부파일 썸네일")
class CommunityAttachmentThumbnailTest {

    private final CommunityAttachmentThumbnail thumbnail = new CommunityAttachmentThumbnail();

    @Test
    @DisplayName("비율을 유지한 JPEG와 원본 키 기반 파생 키를 만든다")
    void createsJpegThumbnailWithDerivedKey() throws Exception {
        byte[] original = png(1200, 600);
        var generated = thumbnail.generate(
                "2026/09/04/original.png",
                attachment(original)
        );

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(generated.bytes()));

        assertAll(
                () -> assertEquals("_thumbnails/v1/480x300/2026/09/04/original.jpg", generated.storageKey()),
                () -> assertNotNull(preview),
                () -> assertEquals(480, preview.getWidth()),
                () -> assertEquals(240, preview.getHeight()),
                () -> assertTrue(generated.bytes().length < original.length)
        );
    }

    @Test
    @DisplayName("헤더만 이미지인 손상 파일은 거절한다")
    void rejectsCorruptedImage() {
        byte[] corrupted = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbnail.generate("2026/09/04/corrupted.png", attachment(corrupted))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    private CommunityAttachmentFile attachment(byte[] bytes) {
        return new CommunityAttachmentFile(
                "original.png",
                "image/png",
                bytes.length,
                0,
                () -> new ByteArrayInputStream(bytes)
        );
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(80, 120, 200, 120));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
