/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.graphql;

import java.io.IOException;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EthGraphQLHttpBySpecTest extends AbstractEthGraphQLHttpServiceTest {

  public static Stream<Arguments> specs() {
    return Stream.of(
        Arguments.of("eth_blockNumber"),
        Arguments.of("eth_call_Block8"),
        Arguments.of("eth_call_Block8_invalidHexBytesData"),
        Arguments.of("eth_call_BlockLatest"),
        Arguments.of("eth_call_from_contract"),
        Arguments.of("eth_estimateGas_transfer"),
        Arguments.of("eth_estimateGas_noParams"),
        Arguments.of("eth_estimateGas_contractDeploy"),
        Arguments.of("eth_estimateGas_from_contract"),
        Arguments.of("eth_gasPrice"),
        Arguments.of("eth_getBalance_0x19"),
        Arguments.of("eth_getBalance_invalidAccountBlockNumber"),
        Arguments.of("eth_getBalance_invalidAccountLatest"),
        Arguments.of("eth_getBalance_latest"),
        Arguments.of("eth_getBalance_toobig_bn"),
        Arguments.of("eth_getBalance_without_addr"),
        Arguments.of("eth_getBlock_byHash"),
        Arguments.of("eth_getBlock_byHash_InvalidHexBytes32Hash"),
        Arguments.of("eth_getBlock_byHashInvalid"),
        Arguments.of("eth_getBlock_byNumber"),
        Arguments.of("eth_getBlock_byNumberInvalid"),
        Arguments.of("eth_getBlock_wrongParams"),
        Arguments.of("eth_getBlockTransactionCount_byHash"),
        Arguments.of("eth_getBlockTransactionCount_byNumber"),
        Arguments.of("eth_getCode"),
        Arguments.of("eth_getCode_noCode"),
        Arguments.of("eth_getLogs_emptyListParam"),
        Arguments.of("eth_getLogs_matchTopic"),
        Arguments.of("eth_getLogs_matchAnyTopic"),
        Arguments.of("eth_getLogs_range"),
        Arguments.of("eth_getStorageAt"),
        Arguments.of("eth_getStorageAt_illegalRangeGreaterThan"),
        Arguments.of("eth_getTransaction_byBlockHashAndIndex"),
        Arguments.of("eth_getTransaction_byBlockNumberAndIndex"),
        Arguments.of("eth_getTransaction_byBlockNumberAndInvalidIndex"),
        Arguments.of("eth_getTransaction_byHash"),
        Arguments.of("eth_getTransaction_byHashNull"),
        Arguments.of("eth_getTransactionCount"),
        Arguments.of("eth_getTransactionReceipt"),
        Arguments.of("eth_sendRawTransaction_contractCreation"),
        Arguments.of("eth_sendRawTransaction_messageCall"),
        Arguments.of("eth_sendRawTransaction_nonceTooLow"),
        Arguments.of("eth_sendRawTransaction_transferEther"),
        Arguments.of("eth_sendRawTransaction_unsignedTransaction"),
        Arguments.of("eth_syncing"),
        Arguments.of("graphql_blocks_byFrom"),
        Arguments.of("graphql_blocks_byRange"),
        Arguments.of("graphql_blocks_byWrongRange"),
        Arguments.of("graphql_pending"),
        Arguments.of("graphql_tooComplex"),
        Arguments.of("graphql_tooComplexSchema"),
        Arguments.of("graphql_variable_address"),
        Arguments.of("graphql_variable_bytes"),
        Arguments.of("graphql_variable_bytes32"),
        Arguments.of("graphql_variable_long"),
        Arguments.of("block_withdrawals_pre_shanghai"),
        Arguments.of("block_withdrawals"),
        Arguments.of("eth_getTransaction_type2"),
        Arguments.of("eth_getBlock_shanghai"));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("specs")
  public void graphQLCallWithSpecFile(final String specFileName) throws Exception {
    graphQLCall(specFileName);
  }

  private void graphQLCall(final String name) throws IOException {
    final String testSpecFile = name + ".json";
    final String json =
        Resources.toString(
            EthGraphQLHttpBySpecTest.class.getResource(testSpecFile), Charsets.UTF_8);
    final JsonObject spec = new JsonObject(json);
    final String rawRequestBody = spec.getString("request");
    final String rawVariables = spec.getString("variables");
    final RequestBody requestBody =
        rawVariables == null
            ? RequestBody.create(rawRequestBody, GRAPHQL)
            : RequestBody.create(
                "{ \"query\":\"" + rawRequestBody + "\", \"variables\": " + rawVariables + "}",
                JSON);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    try (final Response resp = client.newCall(request).execute()) {
      final JsonObject expectedRespBody = spec.getJsonObject("response");
      final String resultStr = resp.body().string();

      final JsonObject result = new JsonObject(resultStr);
      Assertions.assertThat(result).isEqualTo(expectedRespBody);

      final int expectedStatusCode = spec.getInteger("statusCode");
      Assertions.assertThat(resp.code()).isEqualTo(expectedStatusCode);
    }
  }
}
