package ma.rhflow.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.tenant.TenantContext;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final Path root = Path.of("data", "uploads").toAbsolutePath().normalize();
    private final String signingKey;
    private final ObjectProvider<MinioClient> minio;
    private final boolean minioEnabled;
    private final String bucket;
    private volatile boolean bucketReady;

    public FileStorageService(
            @Value("${app.jwt.secret}") String signingKey,
            ObjectProvider<MinioClient> minio,
            @Value("${app.minio.enabled:false}") boolean minioEnabled,
            @Value("${app.minio.bucket:rhflow}") String bucket) {
        this.signingKey = signingKey;
        this.minio = minio;
        this.minioEnabled = minioEnabled;
        this.bucket = bucket;
    }

    public StoredFile store(MultipartFile file) {
        return store(file, 1600);
    }

    public StoredFile store(MultipartFile file, int maxEdge) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Fichier vide");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw BusinessException.badRequest("Fichier trop volumineux (10 Mo maximum)");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(type)) {
            throw BusinessException.badRequest("Format accepté : PDF, Word ou image");
        }
        String original = file.getOriginalFilename() == null ? "document" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        try {
            byte[] bytes = file.getBytes();
            ImageShrink.Result shrunk = ImageShrink.shrink(bytes, type, maxEdge);
            if (shrunk != null) {
                bytes = shrunk.bytes();
                type = shrunk.contentType();
                ext = shrunk.extension();
            }
            String key = tenantFolder() + "/" + UUID.randomUUID() + ext;
            write(key, bytes, type);
            return new StoredFile(key, type, bytes.length, original);
        } catch (IOException e) {
            throw BusinessException.badRequest("Enregistrement du fichier impossible");
        }
    }

    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("..")) {
            return;
        }
        if (minioEnabled) {
            try {
                client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storedName).build());
            } catch (Exception ignored) {
                log.warn("fichier MinIO non supprimé {}", storedName);
            }
        }
        try {
            Path path = root.resolve(storedName).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            log.warn("fichier disque non supprimé {}", storedName);
        }
    }

    public StoredFile storeBytes(byte[] bytes, String contentType, String originalName) {
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("Fichier vide");
        }
        String key = tenantFolder() + "/" + UUID.randomUUID() + ".pdf";
        write(key, bytes, contentType);
        return new StoredFile(key, contentType, bytes.length, originalName);
    }

    public byte[] read(String storedName) {
        if (storedName == null || storedName.contains("..")) {
            throw BusinessException.notFound("Fichier introuvable");
        }
        if (minioEnabled) {
            try {
                return readMinio(storedName);
            } catch (Exception ignored) {
                // Les fichiers déjà sur disque restent lisibles.
            }
        }
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw BusinessException.notFound("Fichier introuvable");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw BusinessException.notFound("Fichier introuvable");
        }
    }

    public void deleteTenantFiles(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        String prefix = tenantId + "/";
        try {
            if (minioEnabled) {
                MinioClient client = client();
                Iterable<Result<Item>> objects = client.listObjects(
                        ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
                for (Result<Item> item : objects) {
                    client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.get().objectName()).build());
                }
            }
            Path dir = root.resolve(String.valueOf(tenantId)).normalize();
            if (dir.startsWith(root) && Files.isDirectory(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            log.warn("fichier non supprimé {}", path);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("purge des fichiers société {} impossible : {}", tenantId, e.getMessage());
        }
    }

    private void write(String key, byte[] bytes, String contentType) {
        if (key.contains("..")) {
            throw BusinessException.badRequest("Chemin de fichier invalide");
        }
        if (minioEnabled) {
            try {
                ensureBucket();
                client().putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                        .build());
                return;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("MinIO indisponible, bascule disque : {}", e.getMessage());
            }
        }
        try {
            Path dest = root.resolve(key).normalize();
            if (!dest.startsWith(root)) {
                throw BusinessException.badRequest("Chemin de fichier invalide");
            }
            Files.createDirectories(dest.getParent());
            Files.write(dest, bytes);
        } catch (IOException e) {
            throw BusinessException.badRequest("Enregistrement du fichier impossible");
        }
    }

    private byte[] readMinio(String key) throws Exception {
        try (InputStream in = client().getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        MinioClient client = client();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }

    private MinioClient client() {
        MinioClient client = minio.getIfAvailable();
        if (client == null) {
            throw BusinessException.badRequest("MinIO n’est pas configuré");
        }
        return client;
    }

    public String sign(String storedName, int minutes) {
        if (storedName == null || storedName.isBlank()) {
            return null;
        }
        long exp = Instant.now().plusSeconds(minutes * 60L).getEpochSecond();
        String payload = storedName + "|" + exp;
        return "/files/signed?token=" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "|" + hmac(payload)).getBytes(StandardCharsets.UTF_8));
    }

    public byte[] readSigned(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int last = raw.lastIndexOf('|');
            if (last < 0) {
                throw BusinessException.forbidden("Lien de fichier invalide");
            }
            String payload = raw.substring(0, last);
            String sig = raw.substring(last + 1);
            if (!MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), hmac(payload).getBytes(StandardCharsets.UTF_8))) {
                throw BusinessException.forbidden("Lien de fichier invalide");
            }
            int split = payload.lastIndexOf('|');
            String path = payload.substring(0, split);
            long exp = Long.parseLong(payload.substring(split + 1));
            if (Instant.now().getEpochSecond() > exp) {
                throw BusinessException.forbidden("Lien de fichier expiré");
            }
            return read(path);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.forbidden("Lien de fichier invalide");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record StoredFile(String storedName, String contentType, long size, String originalName) {
    }

    private String tenantFolder() {
        Long id = TenantContext.getTenantId();
        return id == null ? "platform" : String.valueOf(id);
    }
}
