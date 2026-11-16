package com.blockchaincb.demo.storage;

public interface PrivateStorage {
  String put(byte[] content);

  byte[] read(String key);
}
