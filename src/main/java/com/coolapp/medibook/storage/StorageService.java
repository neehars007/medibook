package com.coolapp.medibook.storage;

import com.coolapp.medibook.common.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Private S3/MinIO bucket. Objects are only reachable through short-lived signed URLs. */
@Slf4j
@Service
public class StorageService {

    private final MinioClient client;
    private final AppProperties.Storage cfg;
    private volatile boolean bucketReady;

    public StorageService(AppProperties props) {
        this.cfg = props.storage();
        this.client = MinioClient.builder()
                .endpoint(cfg.endpoint())
                .credentials(cfg.accessKey(), cfg.secretKey())
                .build();
    }

    public void put(String key, InputStream data, long size, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(cfg.bucket()).object(key)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Upload failed for " + key, e);
        }
    }

    public void put(String key, byte[] data, String contentType) {
        put(key, new ByteArrayInputStream(data), data.length, contentType);
    }

    public byte[] get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(cfg.bucket()).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new StorageException("Download failed for " + key, e);
        }
    }

    public String signedDownloadUrl(String key) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(cfg.bucket())
                    .object(key)
                    .expiry((int) cfg.signedUrlTtl().toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Could not sign URL for " + key, e);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(cfg.bucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(cfg.bucket()).build());
                log.info("Created private bucket {}", cfg.bucket());
            }
            bucketReady = true;
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
