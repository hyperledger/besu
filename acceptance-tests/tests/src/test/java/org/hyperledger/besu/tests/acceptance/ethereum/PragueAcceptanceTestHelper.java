/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.ethereum;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.web3j.protocol.core.methods.response.EthBlock;

public class PragueAcceptanceTestHelper {
  protected static final MediaType MEDIA_TYPE_JSON =
      MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient httpClient;
  private final ObjectMapper mapper;
  private final BesuNode besuNode;
  private final EthTransactions ethTransactions;

  private long blockTimeStamp = 0;

  PragueAcceptanceTestHelper(final BesuNode besuNode, final EthTransactions ethTransactions) {
    this.besuNode = besuNode;
    this.ethTransactions = ethTransactions;
    httpClient = new OkHttpClient();
    mapper = new ObjectMapper();
  }

  public void buildNewBlock() throws IOException {
    final EthBlock.Block block = besuNode.execute(ethTransactions.block());

    blockTimeStamp += 1;
    final Call buildBlockRequest =
        createEngineCall(createForkChoiceRequest(block.getHash(), blockTimeStamp));

    final String payloadId;
    try (final Response buildBlockResponse = buildBlockRequest.execute()) {
      payloadId =
          mapper
              .readTree(buildBlockResponse.body().string())
              .get("result")
              .get("payloadId")
              .asText();

      assertThat(payloadId).isNotEmpty();
    }

    waitFor(500);

    final Call getPayloadRequest = createEngineCall(createGetPayloadRequest(payloadId));

    final ObjectNode executionPayload;
    final ArrayNode executionRequests;
    final String newBlockHash;
    final String parentBeaconBlockRoot;
    try (final Response getPayloadResponse = getPayloadRequest.execute()) {
      assertThat(getPayloadResponse.code()).isEqualTo(200);

      JsonNode result = mapper.readTree(getPayloadResponse.body().string()).get("result");
      executionPayload = (ObjectNode) result.get("executionPayload");
      executionRequests = (ArrayNode) result.get("executionRequests");

      newBlockHash = executionPayload.get("blockHash").asText();
      parentBeaconBlockRoot = executionPayload.remove("parentBeaconBlockRoot").asText();

      assertThat(newBlockHash).isNotEmpty();
    }

    final Call newPayloadRequest =
        createEngineCall(
            createNewPayloadRequest(
                executionPayload.toString(), parentBeaconBlockRoot, executionRequests.toString()));
    try (final Response newPayloadResponse = newPayloadRequest.execute()) {
      assertThat(newPayloadResponse.code()).isEqualTo(200);

      final String responseStatus =
          mapper.readTree(newPayloadResponse.body().string()).get("result").get("status").asText();
      assertThat(responseStatus).isEqualTo("VALID");
    }

    final Call moveChainAheadRequest = createEngineCall(createForkChoiceRequest(newBlockHash));

    try (final Response moveChainAheadResponse = moveChainAheadRequest.execute()) {
      assertThat(moveChainAheadResponse.code()).isEqualTo(200);
    }
  }

  private Call createEngineCall(final String request) {
    return httpClient.newCall(
        new Request.Builder()
            .url(besuNode.engineRpcUrl().get())
            .post(RequestBody.create(request, MEDIA_TYPE_JSON))
            .build());
  }

  private String createForkChoiceRequest(final String blockHash) {
    return createForkChoiceRequest(blockHash, null);
  }

  private String createForkChoiceRequest(final String parentBlockHash, final Long timeStamp) {
    final Optional<Long> maybeTimeStamp = Optional.ofNullable(timeStamp);

    String forkChoiceRequest =
        "{"
            + "  \"jsonrpc\": \"2.0\","
            + "  \"method\": \"engine_forkchoiceUpdatedV3\","
            + "  \"params\": ["
            + "    {"
            + "      \"headBlockHash\": \""
            + parentBlockHash
            + "\","
            + "      \"safeBlockHash\": \""
            + parentBlockHash
            + "\","
            + "      \"finalizedBlockHash\": \""
            + parentBlockHash
            + "\""
            + "    }";

    if (maybeTimeStamp.isPresent()) {
      forkChoiceRequest +=
          "    ,{"
              + "      \"timestamp\": \""
              + Long.toHexString(maybeTimeStamp.get())
              + "\","
              + "      \"prevRandao\": \"0x0000000000000000000000000000000000000000000000000000000000000000\","
              + "      \"suggestedFeeRecipient\": \"0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b\","
              + "      \"withdrawals\": [],"
              + "      \"parentBeaconBlockRoot\": \"0x0000000000000000000000000000000000000000000000000000000000000000\""
              + "    }";
    }

    forkChoiceRequest += "  ]," + "  \"id\": 67" + "}";

    return forkChoiceRequest;
  }

  private String createGetPayloadRequest(final String payloadId) {
    return "{"
        + "  \"jsonrpc\": \"2.0\","
        + "  \"method\": \"engine_getPayloadV4\","
        + "  \"params\": [\""
        + payloadId
        + "\"],"
        + "  \"id\": 67"
        + "}";
  }

  private String createNewPayloadRequest(
      final String executionPayload,
      final String parentBeaconBlockRoot,
      final String executionRequests) {
    return "{"
        + "  \"jsonrpc\": \"2.0\","
        + "  \"method\": \"engine_newPayloadV4\","
        + "  \"params\": ["
        + executionPayload
        + ",[],"
        + "\""
        + parentBeaconBlockRoot
        + "\""
        + ","
        + executionRequests
        + "],"
        + "  \"id\": 67"
        + "}";
  }

  private static void waitFor(final long durationMilliSeconds) {
    try {
      Thread.sleep(durationMilliSeconds);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
