/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.base.Supplier;
import org.testcontainers.shaded.com.google.common.base.Suppliers;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Async;
import org.web3j.utils.Numeric;

public class RollupExtendedAcceptanceTest extends AcceptanceTestBase {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final String GENESIS_FILE = "/jsonrpc/rollup/genesis.json";
  private static final String RANDOM_ACCOUNT_ADDRESS =
      Address.extract(Bytes32.random()).toHexString();
  private static final KeyPair account1KeyPair = keyPair(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);

  private BesuNode executionEngineNode;
  private Web3j web3j;
  private EngineApiClient engineApiClient;

  @Before
  public void setUp() throws Exception {
    executionEngineNode =
        besu.createExecutionEngineGenesisNode("rollup-execution-engine", GENESIS_FILE);
    cluster.start(executionEngineNode);

    engineApiClient =
        new EngineApiClient(httpClient(false), executionEngineNode.engineRpcUrl().orElseThrow());

    final String jsonRpcApiUrl =
        "http://"
            + executionEngineNode.getHostName()
            + ":"
            + executionEngineNode.getJsonRpcPort().orElseThrow();
    web3j =
        Web3j.build(
            new HttpService(jsonRpcApiUrl, httpClient(false)),
            1000,
            Async.defaultExecutorService());
  }

  private static OkHttpClient httpClient(final boolean debugEnabled) {
    OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
    if (debugEnabled) {
      httpClientBuilder
          .readTimeout(Duration.ofMinutes(5))
          .writeTimeout(Duration.ofMinutes(5))
          .callTimeout(Duration.ofMinutes(10))
          .addInterceptor(new HttpLoggingInterceptor().setLevel(Level.BODY));
    }
    return httpClientBuilder.build();
  }

  @Test
  public void createsBlockAndReturnsInvalidAndUnprocessedTransactions() throws Exception {
    final EthBlock.Block finalizedBlock = getBlockByNumber(DefaultBlockParameterName.LATEST);
    final Transaction transaction1 =
        buildTransferTransaction(RANDOM_ACCOUNT_ADDRESS, Wei.of(10), 0L, 23_000L);
    final Transaction transaction2 =
        buildTransferTransaction(RANDOM_ACCOUNT_ADDRESS, Wei.of(10), 10L, 23_000L);
    final Transaction transaction3 =
        buildTransferTransaction(
            RANDOM_ACCOUNT_ADDRESS, Wei.fromHexString("0xad78ebc5ac6200000"), 1L, 23_000L);
    final Transaction transaction4 =
        buildTransferTransaction(RANDOM_ACCOUNT_ADDRESS, Wei.of(10), 1L, 100_000_000L);
    final Transaction transaction5 =
        buildTransferTransaction(RANDOM_ACCOUNT_ADDRESS, Wei.of(12), 1L, 23_000L);
    final String rlpEncodedTransaction1 = rlpEncodeTransaction(transaction1);
    final String rlpEncodedTransaction2 = rlpEncodeTransaction(transaction2);
    final String rlpEncodedTransaction3 = rlpEncodeTransaction(transaction3);
    final String rlpEncodedTransaction4 = rlpEncodeTransaction(transaction4);
    final String rlpEncodedTransaction5 = rlpEncodeTransaction(transaction5);
    final JsonObject result =
        engineApiClient.rollup_createNewBlock(
            finalizedBlock.getHash(),
            rlpEncodedTransaction1,
            rlpEncodedTransaction2,
            rlpEncodedTransaction3,
            rlpEncodedTransaction4,
            rlpEncodedTransaction5);

    final JsonObject newBlock = result.getJsonObject("executionPayload");
    final String newBlockHash = newBlock.getString("blockHash");
    assertThat(newBlock.getJsonArray("transactions")).isNotNull();
    assertThat(newBlock.getJsonArray("transactions").size()).isEqualTo(2);
    assertThat(newBlock.getJsonArray("transactions").getString(0))
        .isEqualTo(rlpEncodedTransaction1);
    assertThat(newBlock.getJsonArray("transactions").getString(1))
        .isEqualTo(rlpEncodedTransaction5);
    assertThat(result.getJsonArray("invalidTransactions").size()).isEqualTo(2);
    var invalidTx1Result = result.getJsonArray("invalidTransactions").getJsonObject(0);
    var invalidTx2Result = result.getJsonArray("invalidTransactions").getJsonObject(1);

    assertThat(invalidTx1Result.getString("transaction")).isEqualTo(rlpEncodedTransaction2);
    assertThat(invalidTx1Result.getString("reason")).isEqualTo("INCORRECT_NONCE");
    assertThat(invalidTx1Result.getString("errorMessage"))
        .isEqualTo("transaction nonce 10 does not match sender account nonce 1.");

    assertThat(invalidTx2Result.getString("transaction")).isEqualTo(rlpEncodedTransaction3);
    assertThat(invalidTx2Result.getString("reason")).isEqualTo("UPFRONT_COST_EXCEEDS_BALANCE");
    assertThat(invalidTx2Result.getString("errorMessage"))
        .isEqualTo(
            "transaction up-front cost 0x00000000000000000000000000000000000000000000000ad78ebc5aca3cdb40 exceeds transaction sender account balance 0x00000000000000000000000000000000000000000000000ad78ebc5ac25eb236");

    var unprocessedTransactions = result.getJsonArray("unprocessedTransactions");
    assertThat(unprocessedTransactions).isNotNull();
    assertThat(unprocessedTransactions.size()).isEqualTo(1);
    assertThat(unprocessedTransactions.getString(0)).isEqualTo(rlpEncodedTransaction4);

    // add new payload to execution engine;
    engineApiClient.engine_newPayloadV1(newBlock);
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST)).isEqualTo(finalizedBlock);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING)).isEqualTo(finalizedBlock);

    engineApiClient.engine_forkchoiceUpdatedV1(
        Map.of(
            "headBlockHash", newBlockHash,
            "safeBlockHash", finalizedBlock.getHash(),
            "finalizedBlockHash", finalizedBlock.getHash()),
        null);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING).getHash())
        .isEqualTo(newBlockHash);
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST).getHash())
        .isEqualTo(newBlockHash);
  }

  @Test
  public void createMultipleBlocks() throws Exception {
    final EthBlock.Block finalizedBlock = getBlockByNumber(DefaultBlockParameterName.LATEST);
    final JsonObject block1 =
        engineApiClient
            .rollup_createNewBlock(finalizedBlock.getHash())
            .getJsonObject("executionPayload");
    final String block1Hash = block1.getString("blockHash");
    engineApiClient.engine_newPayloadV1(block1);

    Thread.sleep(1000);
    final JsonObject block2 =
        engineApiClient.rollup_createNewBlock(block1Hash).getJsonObject("executionPayload");
    final String block2Hash = block2.getString("blockHash");
    engineApiClient.engine_newPayloadV1(block2);

    Thread.sleep(1000);
    final JsonObject block3 =
        engineApiClient.rollup_createNewBlock(block2Hash).getJsonObject("executionPayload");
    final String block3Hash = block3.getString("blockHash");
    engineApiClient.engine_newPayloadV1(block3);

    // it does not change the chain;
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST)).isEqualTo(finalizedBlock);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING)).isEqualTo(finalizedBlock);

    engineApiClient.engine_forkchoiceUpdatedV1(
        Map.of(
            "headBlockHash", block3Hash,
            "safeBlockHash", finalizedBlock.getHash(),
            "finalizedBlockHash", finalizedBlock.getHash()),
        null);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING).getHash()).isEqualTo(block3Hash);
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST).getHash()).isEqualTo(block3Hash);
  }

  @Test
  public void createForkingBlocks() throws Exception {
    final EthBlock.Block finalizedBlock = getBlockByNumber(DefaultBlockParameterName.LATEST);
    final JsonObject block1 =
        engineApiClient
            .rollup_createNewBlock(finalizedBlock.getHash())
            .getJsonObject("executionPayload");
    final String block1Hash = block1.getString("blockHash");
    engineApiClient.engine_newPayloadV1(block1);

    final JsonObject block2 =
        engineApiClient
            .rollup_createNewBlock(finalizedBlock.getHash())
            .getJsonObject("executionPayload");
    engineApiClient.engine_newPayloadV1(block2);

    Thread.sleep(1000);
    final JsonObject block3 =
        engineApiClient.rollup_createNewBlock(block1Hash).getJsonObject("executionPayload");
    final String block3Hash = block3.getString("blockHash");
    engineApiClient.engine_newPayloadV1(block3);

    // it does not change the chain;
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST)).isEqualTo(finalizedBlock);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING)).isEqualTo(finalizedBlock);

    engineApiClient.engine_forkchoiceUpdatedV1(
        Map.of(
            "headBlockHash", block3Hash,
            "safeBlockHash", finalizedBlock.getHash(),
            "finalizedBlockHash", finalizedBlock.getHash()),
        null);
    assertThat(getBlockByNumber(DefaultBlockParameterName.PENDING).getHash()).isEqualTo(block3Hash);
    assertThat(getBlockByNumber(DefaultBlockParameterName.LATEST).getHash()).isEqualTo(block3Hash);
    final var block1Obj = getBlockByHash(block1Hash);
    assertThat(block1Obj).isNotNull();
    assertThat(getBlockByNumber(DefaultBlockParameter.valueOf(block1Obj.getNumber())))
        .isEqualTo(block1Obj);
    assertThat(getBlockByHash(block3Hash)).isNotNull();
  }

  private EthBlock.Block getBlockByNumber(final DefaultBlockParameter blockNumber)
      throws IOException {
    return web3j.ethGetBlockByNumber(blockNumber, false).send().getBlock();
  }

  private EthBlock.Block getBlockByHash(final String blockHash) throws IOException {
    return web3j.ethGetBlockByHash(blockHash, false).send().getBlock();
  }

  private Transaction buildTransferTransaction(
      final String to, final Wei value, final long nonce, final long gasLimit) {
    return Transaction.builder()
        .chainId(new BigInteger("1", 10))
        .nonce(nonce)
        .to(Address.fromHexString(to))
        .value(value)
        .gasLimit(gasLimit)
        .gasPrice(Wei.of(3000))
        .payload(Bytes.EMPTY)
        .type(TransactionType.FRONTIER)
        .signAndBuild(account1KeyPair);
  }

  private static String rlpEncodeTransaction(final Transaction transaction) {
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    transaction.writeTo(rlp);
    return rlp.encoded().toHexString();
  }

  private static KeyPair keyPair(final String privateKey) {
    final SignatureAlgorithm signatureAlgorithm = SIGNATURE_ALGORITHM.get();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(privateKey)));
  }

  private static class EngineApiClient {

    private final OkHttpClient consensusClient;
    private final String executionEngineApiUrl;

    public EngineApiClient(final OkHttpClient consensusClient, final String executionEngineApiUrl) {
      this.consensusClient = consensusClient;
      this.executionEngineApiUrl = executionEngineApiUrl;
    }

    private void engine_newPayloadV1(final JsonObject executionPayload) throws Exception {
      final JsonObject result =
          makeRequestAndGetResult(rpcRequest("engine_newPayloadV1", executionPayload));
      assertThat(result.getString("status"))
          .withFailMessage("expected new payload to be Accepted, but got" + result)
          .isEqualTo("VALID");
    }

    private String engine_forkchoiceUpdatedV1(
        final Map<String, String> forkChoiceAttributes, final Map<String, String> payloadAttributes)
        throws Exception {
      final JsonObject result =
          makeRequestAndGetResult(
              rpcRequest("engine_forkchoiceUpdatedV1", forkChoiceAttributes, payloadAttributes));
      assertThat(result.getJsonObject("payloadStatus").getString("status"))
          .withFailMessage("expected VALID forkChoice but got " + result)
          .isEqualTo("VALID");

      return result.getString("payloadId");
    }

    @SuppressWarnings("UnusedMethod")
    private JsonObject engine_getPayload(final String payloadId) throws Exception {
      return makeRequestAndGetResult(rpcRequest("engine_getPayloadV1", payloadId));
    }

    private JsonObject rollup_createNewBlock(
        final String parentBlockHash, final String... transactions) throws Exception {
      return makeRequestAndGetResult(
          rpcRequest(
              "rollup_createBlockV1",
              parentBlockHash,
              transactions,
              Bytes32.random().toHexString(), // prevRandao
              Address.extract(Bytes32.random()).toHexString(), // feeRecipient
              "0xa400", // blockGasLimit
              Numeric.toHexStringWithPrefix(BigInteger.valueOf(Instant.now().getEpochSecond()))
              // timestamp
              ));
    }

    private JsonObject makeRequestAndGetResult(final JsonObject rpcRequest) throws IOException {
      final Response response = makeRequest(rpcRequest);

      final JsonObject responseJson = new JsonObject(response.body().string());
      final JsonObject result = responseJson.getJsonObject("result");
      assertThat(result).isNotNull();
      return result;
    }

    private Response makeRequest(final JsonObject rpcRequest) throws IOException {
      return consensusClient
          .newCall(
              new Request.Builder()
                  .url(executionEngineApiUrl)
                  .post(RequestBody.create(rpcRequest.toString(), MEDIA_TYPE_JSON))
                  .build())
          .execute();
    }

    private static JsonObject rpcRequest(final String method, final Object... params) {
      return new JsonObject()
          .put("id", "100")
          .put("jsonrpc", "2.0")
          .put("method", method)
          .put("params", params);
    }
  }
}
