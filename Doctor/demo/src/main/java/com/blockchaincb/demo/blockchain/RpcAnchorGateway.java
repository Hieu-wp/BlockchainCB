package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.exception.ApiException;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;
import org.web3j.abi.*;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.*;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/** Read-only JSON-RPC. No credentials, signer, transaction manager or sendRawTransaction. */
@Component
public class RpcAnchorGateway implements AnchorGateway {
  private final ChainSettings settings;
  private Web3j client;
  private static final String EVENT =
      Hash.sha3String("VersionAnchored(bytes32,uint64,bytes32,bytes32,address)");

  public RpcAnchorGateway(ChainSettings settings) {
    this.settings = settings;
  }

  private synchronized Web3j rpc() {
    settings.requireEnabled();
    if (client == null) {
      var http =
          new okhttp3.OkHttpClient.Builder()
              .connectTimeout(java.time.Duration.ofSeconds(5))
              .readTimeout(java.time.Duration.ofSeconds(10))
              .callTimeout(java.time.Duration.ofSeconds(15))
              .build();
      client = Web3j.build(new HttpService(settings.rpc, http, false));
    }
    return client;
  }

  private <T extends Response<?>> T send(Request<?, T> req) {
    try {
      T result = req.send();
      if (result.hasError()) throw new ApiException(503, "RPC_UNAVAILABLE");
      return result;
    } catch (Exception e) {
      throw new ApiException(503, "RPC_UNAVAILABLE");
    }
  }

  private void checkNetwork() {
    if (!send(rpc().ethChainId()).getChainId().equals(BigInteger.valueOf(ChainSettings.CHAIN)))
      throw new ApiException(503, "RPC_WRONG_CHAIN");
    String code =
        send(rpc().ethGetCode(ChainSettings.CONTRACT, DefaultBlockParameterName.LATEST)).getCode();
    if (code == null || code.equals("0x") || code.equals("0x0"))
      throw new ApiException(503, "CONTRACT_CODE_MISSING");
    try {
      String expected =
          new String(
                  getClass().getResourceAsStream("/anchor-runtime-hash.txt").readAllBytes(),
                  java.nio.charset.StandardCharsets.US_ASCII)
              .trim();
      if (!Hash.sha3(code).equalsIgnoreCase(expected))
        throw new ApiException(503, "CONTRACT_CODE_MISMATCH");
    } catch (java.io.IOException | NullPointerException e) {
      throw new ApiException(503, "CONTRACT_METADATA_MISSING");
    }
  }

  private List<Type> call(String name, List<Type> args, List<TypeReference<?>> outputs) {
    checkNetwork();
    var f = new Function(name, args, outputs);
    var tx =
        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
            null, ChainSettings.CONTRACT, FunctionEncoder.encode(f));
    var response = send(rpc().ethCall(tx, DefaultBlockParameterName.LATEST));
    if (response.isReverted()) throw new ApiException(503, "CONTRACT_READ_REVERTED");
    var decoded = FunctionReturnDecoder.decode(response.getValue(), f.getOutputParameters());
    if (decoded.size() != outputs.size()) throw new ApiException(503, "CONTRACT_RESPONSE_INVALID");
    return decoded;
  }

  private static Bytes32 bytes(String hex) {
    return new Bytes32(Numeric.hexStringToByteArray(ChainSettings.hex32(hex)));
  }

  public State state(String key) {
    var r =
        call(
            "records",
            List.of(bytes(key)),
            List.of(
                new TypeReference<Address>() {},
                new TypeReference<Uint64>() {},
                new TypeReference<Bytes32>() {}));
    return new State(
        r.get(0).getValue().toString(),
        ((BigInteger) r.get(1).getValue()).longValueExact(),
        Numeric.toHexString((byte[]) r.get(2).getValue()));
  }

  public String versionHash(String key, long n) {
    return Numeric.toHexString(
        (byte[])
            call(
                    "versionHashes",
                    List.of(bytes(key), new Uint64(n)),
                    List.of(new TypeReference<Bytes32>() {}))
                .get(0)
                .getValue());
  }

  public boolean doctorRole(String wallet) {
    var role =
        (byte[])
            call("DOCTOR_ROLE", List.of(), List.of(new TypeReference<Bytes32>() {}))
                .get(0)
                .getValue();
    return (Boolean)
        call(
                "hasRole",
                List.of(new Bytes32(role), new Address(wallet)),
                List.of(new TypeReference<Bool>() {}))
            .get(0)
            .getValue();
  }

  public boolean writePermission(String patient, String doctor) {
    return (Boolean)
        call(
                "writePermission",
                List.of(new Address(patient), new Address(doctor)),
                List.of(new TypeReference<Bool>() {}))
            .get(0)
            .getValue();
  }

  public long head() {
    checkNetwork();
    return send(rpc().ethBlockNumber()).getBlockNumber().longValueExact();
  }

  public static String input(Payload p) {
    return FunctionEncoder.encode(
        new Function(
            "anchorVersion",
            List.of(
                bytes(p.recordKey()),
                new Address(p.patientWallet()),
                new Uint64(p.versionNumber()),
                bytes(p.contentHash()),
                bytes(p.previousHash())),
            List.of()));
  }

  public Verified verify(Payload p, String hash) {
    checkNetwork();
    hash = ChainSettings.hex32(hash);
    var tx = send(rpc().ethGetTransactionByHash(hash)).getTransaction();
    var receipt = send(rpc().ethGetTransactionReceipt(hash)).getTransactionReceipt();
    if (tx.isEmpty()) return new Verified("PENDING", "TX_NOT_FOUND", null, null);
    var t = tx.get();
    if (!hash.equalsIgnoreCase(t.getHash())
        || !p.contractAddress().equalsIgnoreCase(t.getTo())
        || !p.doctorWallet().equalsIgnoreCase(t.getFrom())
        || !input(p).equalsIgnoreCase(t.getInput())
        || t.getValue().signum() != 0) throw new ApiException(422, "TRANSACTION_DATA_MISMATCH");
    if (receipt.isEmpty()) return new Verified("PENDING", "RECEIPT_PENDING", null, null);
    var r = receipt.get();
    if (!hash.equalsIgnoreCase(r.getTransactionHash())
        || !p.contractAddress().equalsIgnoreCase(r.getTo())
        || !p.doctorWallet().equalsIgnoreCase(r.getFrom()))
      throw new ApiException(422, "RECEIPT_MISMATCH");
    var block =
        send(rpc().ethGetBlockByNumber(DefaultBlockParameter.valueOf(r.getBlockNumber()), false))
            .getBlock();
    if (block == null || !block.getHash().equalsIgnoreCase(r.getBlockHash()))
      return new Verified("PENDING", "REORG_WAIT", null, null);
    long number = r.getBlockNumber().longValueExact();
    if (send(rpc().ethBlockNumber()).getBlockNumber().longValueExact() - number + 1
        < settings.confirmations)
      return new Verified("PENDING", "CONFIRMATIONS_PENDING", number, r.getBlockHash());
    if (!r.isStatusOK())
      return new Verified("FAILED", "TRANSACTION_REVERTED", number, r.getBlockHash());
    String data =
        Numeric.prependHexPrefix(
            FunctionEncoder.encodeConstructor(
                List.of(
                    new Uint64(p.versionNumber()),
                    bytes(p.contentHash()),
                    bytes(p.previousHash()))));
    String doctorTopic =
        "0x" + "0".repeat(24) + p.doctorWallet().substring(2).toLowerCase(Locale.ROOT);
    boolean event =
        r.getLogs().stream()
            .anyMatch(
                l ->
                    p.contractAddress().equalsIgnoreCase(l.getAddress())
                        && l.getTopics().size() == 3
                        && EVENT.equalsIgnoreCase(l.getTopics().get(0))
                        && p.recordKey().equalsIgnoreCase(l.getTopics().get(1))
                        && doctorTopic.equalsIgnoreCase(l.getTopics().get(2))
                        && data.equalsIgnoreCase(l.getData()));
    State s = state(p.recordKey());
    boolean verified =
        (Boolean)
            call(
                    "verifyVersion",
                    List.of(
                        bytes(p.recordKey()),
                        new Uint64(p.versionNumber()),
                        bytes(p.contentHash())),
                    List.of(new TypeReference<Bool>() {}))
                .get(0)
                .getValue();
    if (!event
        || !verified
        || !versionHash(p.recordKey(), p.versionNumber()).equalsIgnoreCase(p.contentHash())
        || !s.patientWallet().equalsIgnoreCase(p.patientWallet())
        || s.latestVersion() < p.versionNumber()
        || (p.versionNumber() > 1
            && !versionHash(p.recordKey(), p.versionNumber() - 1)
                .equalsIgnoreCase(p.previousHash())))
      throw new ApiException(422, "ONCHAIN_DATA_MISMATCH");
    return new Verified("CONFIRMED", "VERIFIED", number, r.getBlockHash());
  }

  public Scan scan(Payload expected, long fromBlock) {
    long tip = head();
    if (fromBlock > tip) return new Scan(null, fromBlock);
    long to = Math.min(tip, fromBlock + 1999);
    var filter =
        new EthFilter(
            DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
            DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
            ChainSettings.CONTRACT);
    filter.addSingleTopic(EVENT).addSingleTopic(expected.recordKey());
    for (var result : send(rpc().ethGetLogs(filter)).getLogs()) {
      var log = (org.web3j.protocol.core.methods.response.Log) result.get();
      String data =
          Numeric.prependHexPrefix(
              FunctionEncoder.encodeConstructor(
                  List.of(
                      new Uint64(expected.versionNumber()),
                      bytes(expected.contentHash()),
                      bytes(expected.previousHash()))));
      if (data.equalsIgnoreCase(log.getData()))
        return new Scan(log.getTransactionHash(), fromBlock);
    }
    // Rescan the final unfinalized blocks to survive short reorgs.
    return new Scan(
        null, to == tip ? Math.max(fromBlock, to - settings.confirmations + 1) : to + 1);
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    if (client != null) client.shutdown();
  }
}
