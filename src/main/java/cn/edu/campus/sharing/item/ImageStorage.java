package cn.edu.campus.sharing.item;

import cn.edu.campus.sharing.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000;
    private final Path root;

    public ImageStorage(@Value("${app.upload-dir:./uploads}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<String> store(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 2) {
            throw badImage("每件物品必须上传1至2张图片");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Image writes require a transaction");
        }
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            NormalizedImage normalized = normalize(file);
            String name = UUID.randomUUID() + "." + normalized.extension();
            Path destination = resolve(name);
            try {
                Files.createDirectories(root);
                Files.write(destination, normalized.bytes(), StandardOpenOption.CREATE_NEW);
            } catch (IOException ex) {
                deleteQuietly(name);
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "图片保存失败，请稍后重试");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) deleteQuietly(name);
                }
            });
            paths.add(name);
        }
        return paths;
    }

    public void deleteAfterCommit(List<String> paths) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { paths.forEach(ImageStorage.this::deleteQuietly); }
        });
    }

    public byte[] read(String name) {
        Path path = resolve(name);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "图片不存在");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "图片不存在");
        }
    }

    private NormalizedImage normalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw badImage("图片不能为空且每张不能超过5MB");
        }
        String mime = file.getContentType();
        if (!"image/png".equalsIgnoreCase(mime) && !"image/jpeg".equalsIgnoreCase(mime)) {
            throw badImage("仅支持 PNG 和 JPEG 图片");
        }
        try (InputStream raw = file.getInputStream();
             var input = new MemoryCacheImageInputStream(raw)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badImage("无法识别图片内容");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") && mime.equalsIgnoreCase("image/png"))
                        && !(format.equals("jpeg") && mime.equalsIgnoreCase("image/jpeg"))) {
                    throw badImage("图片实际格式与声明类型不一致");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > MAX_PIXELS) {
                    throw badImage("图片边长不能超过4096像素，总像素不能超过1200万");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw badImage("图片已损坏");
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    // Re-encode pixels only; metadata and appended payload are not served back.
                    if (!ImageIO.write(decoded, format, output)) throw badImage("图片格式不受支持");
                    if (output.size() > MAX_BYTES) throw badImage("图片处理后超过5MB，请缩小图片后重试");
                    return new NormalizedImage(output.toByteArray(), format.equals("jpeg") ? "jpg" : "png");
                } finally {
                    decoded.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw badImage("图片已损坏或无法读取");
        }
    }

    private Path resolve(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.(png|jpg)")) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        Path result = root.resolve(name).normalize();
        if (!result.startsWith(root)) throw new BusinessException(HttpStatus.NOT_FOUND, "图片不存在");
        return result;
    }

    private void deleteQuietly(String name) {
        try { Files.deleteIfExists(resolve(name)); }
        catch (IOException ex) { log.warn("Could not clean up image {}", name); }
    }

    private BusinessException badImage(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }

    private record NormalizedImage(byte[] bytes, String extension) {}
}
