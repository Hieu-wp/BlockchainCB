package com.blockchaincb.demo.storage;

import com.blockchaincb.demo.exception.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opt-in development adapter. Files are never served as static resources. */
@Component
public class LocalPrivateStorage implements PrivateStorage {
  private final boolean enabled;
  private final Path root;

  public LocalPrivateStorage(
      @Value("${doctor.storage.local-enabled:false}") boolean enabled,
      @Value("${doctor.storage.local-root:./.private-doctor-files}") String root) {
    this.enabled = enabled;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  private Path path(String key) {
    if (!enabled) throw new ApiException(503, "Private storage chua duoc cau hinh");
    if (!key.matches("[a-f0-9-]{36}")) throw new ApiException(404, "Khong tim thay tep");
    Path p = root.resolve(key).normalize();
    if (!p.getParent().equals(root)) throw new ApiException(404, "Khong tim thay tep");
    return p;
  }

  public String put(byte[] content) {
    String key = UUID.randomUUID().toString();
    Path file = path(key);
    try {
      Files.createDirectories(root);
      Files.write(file, content, StandardOpenOption.CREATE_NEW);
      return key;
    } catch (IOException e) {
      throw new ApiException(503, "Khong the luu tep");
    }
  }

  public byte[] read(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (IOException e) {
      throw new ApiException(404, "Khong tim thay tep");
    }
  }
}
