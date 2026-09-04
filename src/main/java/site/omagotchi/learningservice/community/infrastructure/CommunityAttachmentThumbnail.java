package site.omagotchi.learningservice.community.infrastructure;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.global.exception.BusinessException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 커뮤니티 첨부 이미지의 목록/상세 미리보기용 JPEG를 만든다.
 *
 * <p>썸네일 키는 원본 키만으로 결정되므로 DB 컬럼이 필요 없다. 규격이 바뀌면 prefix의
 * 버전을 올려 기존 썸네일과 충돌하지 않게 한다.</p>
 */
@Component
public class CommunityAttachmentThumbnail {

    public static final String CONTENT_TYPE = "image/jpeg";

    private static final String KEY_PREFIX = "_thumbnails/v1/480x300/";
    private static final int MAX_WIDTH = 480;
    private static final int MAX_HEIGHT = 300;
    private static final long MAX_PIXELS = 25_000_000L;

    public Generated generate(String originalStorageKey, CommunityAttachmentFile attachmentFile) {
        BufferedImage source = readFirstFrame(attachmentFile);
        try {
            BufferedImage resized = Thumbnails.of(source)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .keepAspectRatio(true)
                    .asBufferedImage();

            // 투명 PNG/GIF도 JPEG에서 검은 배경이 되지 않도록 흰색 바탕에 합성한다.
            BufferedImage rgb = new BufferedImage(
                    resized.getWidth(),
                    resized.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(resized, 0, 0, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(rgb, "jpg", output)) {
                throw new IOException("JPEG writer를 찾지 못했습니다.");
            }
            return new Generated(storageKey(originalStorageKey), output.toByteArray());
        } catch (IOException exception) {
            throw invalidAttachment(exception);
        }
    }

    public String storageKey(String originalStorageKey) {
        int extensionIndex = originalStorageKey.lastIndexOf('.');
        String keyWithoutExtension = extensionIndex > originalStorageKey.lastIndexOf('/')
                ? originalStorageKey.substring(0, extensionIndex)
                : originalStorageKey;
        return KEY_PREFIX + keyWithoutExtension + ".jpg";
    }

    /**
     * 전체 디코딩 전에 크기를 확인해 작은 압축 파일이 과도한 메모리를 쓰는 것을 막는다.
     * GIF는 미리보기이므로 첫 프레임만 사용한다.
     */
    private BufferedImage readFirstFrame(CommunityAttachmentFile attachmentFile) {
        try (InputStream inputStream = attachmentFile.openStream();
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                throw invalidAttachment(null);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw invalidAttachment(null);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw invalidAttachment(null);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidAttachment(null);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidAttachment(exception);
        }
    }

    private BusinessException invalidAttachment(Throwable cause) {
        return cause == null
                ? new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT)
                : new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT, cause);
    }

    public record Generated(String storageKey, byte[] bytes) {
    }
}
