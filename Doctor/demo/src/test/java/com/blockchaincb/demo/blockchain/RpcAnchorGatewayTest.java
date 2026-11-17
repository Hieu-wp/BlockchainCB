package com.blockchaincb.demo.blockchain;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/** Runs against a real HTTP JSON-RPC stub on loopback; never contacts Sepolia. */
class RpcAnchorGatewayTest {
  HttpServer server;
  RpcAnchorGateway gateway;
  ObjectMapper mapper = new ObjectMapper();
  String doctor = AnchorServiceTest.DOCTOR,
      patient = AnchorServiceTest.PATIENT,
      key = AnchorServiceTest.KEY,
      hash = AnchorServiceTest.HASH,
      txHash = AnchorServiceTest.TX;
  String blockHash = "0x" + "6".repeat(64), input, to, from, receiptStatus = "0x1";
  boolean pending, missingTx, badStorage, badEvent, rpcFailure, reorg;
  long tip = 102;
  AnchorGateway.Payload payload;

  @BeforeEach
  void start() throws Exception {
    payload =
        new AnchorGateway.Payload(
            key,
            patient,
            1,
            hash,
            ChainSettings.ZERO,
            ChainSettings.CONTRACT,
            ChainSettings.CHAIN,
            doctor);
    input = RpcAnchorGateway.input(payload);
    to = ChainSettings.CONTRACT;
    from = doctor;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            var req = mapper.readTree(exchange.getRequestBody());
            String method = req.path("method").asText();
            Map<String, Object> result = new HashMap<>();
            result.put("jsonrpc", "2.0");
            result.put("id", req.get("id"));
            if (rpcFailure)
              result.put("error", Map.of("code", -32000, "message", "test RPC timeout"));
            else result.put("result", answer(method, req.path("params")));
            byte[] bytes = mapper.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (Exception e) {
            exchange.sendResponseHeaders(500, -1);
          } finally {
            exchange.close();
          }
        });
    server.start();
    gateway =
        new RpcAnchorGateway(
            new ChainSettings(
                true, true, "http://127.0.0.1:" + server.getAddress().getPort(), "localhost", 3));
  }

  @AfterEach
  void stop() {
    gateway.close();
    server.stop(0);
  }

  String encode(List<Type> values) {
    return Numeric.prependHexPrefix(FunctionEncoder.encodeConstructor(values));
  }

  Object answer(String method, JsonNode params) throws Exception {
    return switch (method) {
      case "eth_chainId" -> "0xaa36a7";
      case "eth_getCode" ->
          new String(
                  getClass().getResourceAsStream("/anchor-test-runtime.txt").readAllBytes(),
                  StandardCharsets.US_ASCII)
              .trim();
      case "eth_blockNumber" -> Numeric.toHexStringWithPrefix(BigInteger.valueOf(tip));
      case "eth_getTransactionByHash" ->
          missingTx
              ? null
              : Map.of(
                  "hash",
                  txHash,
                  "from",
                  from,
                  "to",
                  to,
                  "input",
                  input,
                  "value",
                  "0x0",
                  "blockHash",
                  blockHash,
                  "blockNumber",
                  "0x64",
                  "nonce",
                  "0x1",
                  "chainId",
                  "0xaa36a7");
      case "eth_getTransactionReceipt" ->
          pending
              ? null
              : Map.of(
                  "transactionHash",
                  txHash,
                  "from",
                  doctor,
                  "to",
                  ChainSettings.CONTRACT,
                  "status",
                  receiptStatus,
                  "blockNumber",
                  "0x64",
                  "blockHash",
                  blockHash,
                  "logs",
                  List.of(log()));
      case "eth_getBlockByNumber" ->
          Map.of("hash", reorg ? ChainSettings.ZERO : blockHash, "number", "0x64");
      case "eth_getLogs" -> List.of(log());
      case "eth_call" -> {
        String data = params.get(0).path("data").asText();
        String selector = data.substring(0, 10);
        if (selector.equals(selector("records(bytes32)")))
          yield encode(
              List.of(
                  new Address(patient),
                  new Uint64(1),
                  new Bytes32(Numeric.hexStringToByteArray(hash))));
        if (selector.equals(selector("versionHashes(bytes32,uint64)")))
          yield encode(
              List.of(
                  new Bytes32(
                      Numeric.hexStringToByteArray(badStorage ? ChainSettings.ZERO : hash))));
        if (selector.equals(selector("DOCTOR_ROLE()")))
          yield encode(
              List.of(new Bytes32(Numeric.hexStringToByteArray(Hash.sha3String("DOCTOR_ROLE")))));
        yield encode(List.of(new Bool(true)));
      }
      default -> throw new IllegalArgumentException("Unknown mock method");
    };
  }

  String selector(String function) {
    return Hash.sha3String(function).substring(0, 10);
  }

  Map<String, Object> log() {
    return Map.of(
        "address",
        ChainSettings.CONTRACT,
        "topics",
        List.of(
            Hash.sha3String("VersionAnchored(bytes32,uint64,bytes32,bytes32,address)"),
            key,
            "0x" + "0".repeat(24) + doctor.substring(2)),
        "data",
        encode(
            List.of(
                new Uint64(1),
                new Bytes32(Numeric.hexStringToByteArray(badEvent ? ChainSettings.ZERO : hash)),
                new Bytes32(new byte[32]))),
        "transactionHash",
        txHash,
        "blockHash",
        blockHash,
        "blockNumber",
        "0x64",
        "logIndex",
        "0x0",
        "transactionIndex",
        "0x0",
        "removed",
        false);
  }

  @Test
  void successfulReceiptChecksInputEventStorageAndConfirmations() {
    assertEquals("CONFIRMED", gateway.verify(payload, txHash).status());
  }

  @Test
  void calldataMatchesViemEncodingOfActualAbi() throws Exception {
    var v = mapper.readTree(getClass().getResourceAsStream("/anchor-calldata-vector.json"));
    assertEquals(v.path("input").asText(), RpcAnchorGateway.input(payload));
  }

  @Test
  void unknownTransactionRemainsPending() {
    missingTx = true;
    assertEquals("PENDING", gateway.verify(payload, txHash).status());
  }

  @Test
  void receiptPendingRemainsPending() {
    pending = true;
    assertEquals("PENDING", gateway.verify(payload, txHash).status());
  }

  @Test
  void insufficientConfirmationsRemainPending() {
    tip = 100;
    assertEquals("PENDING", gateway.verify(payload, txHash).status());
  }

  @Test
  void revertIsFailed() {
    receiptStatus = "0x0";
    assertEquals("FAILED", gateway.verify(payload, txHash).status());
  }

  @Test
  void otherContractRejected() {
    to = "0x" + "9".repeat(40);
    assertEquals(
        422, assertThrows(ApiException.class, () -> gateway.verify(payload, txHash)).getStatus());
  }

  @Test
  void wrongSenderRejected() {
    from = "0x" + "9".repeat(40);
    assertEquals(
        422, assertThrows(ApiException.class, () -> gateway.verify(payload, txHash)).getStatus());
  }

  @Test
  void changedCalldataHashRejected() {
    input = input.substring(0, input.length() - 64) + "a".repeat(64);
    assertEquals(
        422, assertThrows(ApiException.class, () -> gateway.verify(payload, txHash)).getStatus());
  }

  @Test
  void wrongOnchainHashRejected() {
    badStorage = true;
    assertEquals(
        422, assertThrows(ApiException.class, () -> gateway.verify(payload, txHash)).getStatus());
  }

  @Test
  void incorrectEventRejected() {
    badEvent = true;
    assertEquals(
        422, assertThrows(ApiException.class, () -> gateway.verify(payload, txHash)).getStatus());
  }

  @Test
  void rpcFailureIsRetryableWithoutLeakingRpcMessage() {
    rpcFailure = true;
    var e = assertThrows(ApiException.class, () -> gateway.verify(payload, txHash));
    assertEquals(503, e.getStatus());
    assertEquals("RPC_UNAVAILABLE", e.getMessage());
  }

  @Test
  void socketTimeoutDoesNotLeakUrlOrRequestPayload() throws Exception {
    var client = org.mockito.Mockito.mock(org.web3j.protocol.Web3j.class);
    @SuppressWarnings("unchecked")
    org.web3j.protocol.core.Request<?, org.web3j.protocol.core.methods.response.EthChainId>
        request = org.mockito.Mockito.mock(org.web3j.protocol.core.Request.class);
    org.mockito.Mockito.doReturn(request).when(client).ethChainId();
    org.mockito.Mockito.when(request.send())
        .thenThrow(new java.net.SocketTimeoutException("mock transport timeout"));
    var field = RpcAnchorGateway.class.getDeclaredField("client");
    field.setAccessible(true);
    field.set(gateway, client);
    var error = assertThrows(ApiException.class, () -> gateway.verify(payload, txHash));
    assertEquals(503, error.getStatus());
    assertEquals("RPC_UNAVAILABLE", error.getMessage());
  }

  @Test
  void reorgRemainsPending() {
    reorg = true;
    assertEquals("PENDING", gateway.verify(payload, txHash).status());
  }

  @Test
  void scannerFindsBroadcastAfterReactCloses() {
    assertEquals(txHash, gateway.scan(payload, 99).transactionHash());
  }
}
