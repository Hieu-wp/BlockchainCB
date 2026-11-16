package com.blockchaincb.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.storage.LocalPrivateStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateStorageTest {
  @TempDir Path temp;

  @Test
  void disabledAdapterFailsExplicitly() {
    var storage = new LocalPrivateStorage(false, temp.toString());
    assertEquals(
        503, assertThrows(ApiException.class, () -> storage.put(new byte[] {1})).getStatus());
  }

  @Test
  void serverGeneratedKeysRoundTripAndRejectTraversal() {
    var storage = new LocalPrivateStorage(true, temp.toString());
    byte[] bytes = {1, 2, 3};
    String key = storage.put(bytes);
    assertArrayEquals(bytes, storage.read(key));
    assertThrows(ApiException.class, () -> storage.read("../secret"));
  }
}
