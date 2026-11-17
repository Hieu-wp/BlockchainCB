package com.blockchaincb.demo.blockchain;

public interface AnchorGateway {
  record State(String patientWallet, long latestVersion, String latestHash) {}

  record Payload(
      String recordKey,
      String patientWallet,
      long versionNumber,
      String contentHash,
      String previousHash,
      String contractAddress,
      long chainId,
      String doctorWallet) {}

  record Verified(String status, String code, Long blockNumber, String blockHash) {}

  record Scan(String transactionHash, long nextBlock) {}

  State state(String key);

  String versionHash(String key, long number);

  boolean doctorRole(String wallet);

  boolean writePermission(String patient, String doctor);

  long head();

  Verified verify(Payload expected, String txHash);

  Scan scan(Payload expected, long fromBlock);
}
