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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EthGraphQLHttpBySpecTest extends AbstractEthGraphQLHttpServiceTest {

  private final String specFileName;

  public EthGraphQLHttpBySpecTest(final String specFileName) {
    this.specFileName = specFileName;
  }

  @Parameters(name = "{index}: {0}")
  public static Collection<String> specs() {
    final List<String> specs = new ArrayList<>();

    specs.add("eth_blockNumber");

    specs.add("eth_call_Block8");
    specs.add("eth_call_Block8_invalidHexBytesData");
    specs.add("eth_call_BlockLatest");
    specs.add("eth_call_from_contract");

    specs.add("eth_estimateGas_transfer");
    specs.add("eth_estimateGas_noParams");
    specs.add("eth_estimateGas_contractDeploy");
    specs.add("eth_estimateGas_from_contract");

    specs.add("eth_gasPrice");

    specs.add("eth_getBalance_0x19");
    specs.add("eth_getBalance_invalidAccountBlockNumber");
    specs.add("eth_getBalance_invalidAccountLatest");
    specs.add("eth_getBalance_latest");
    specs.add("eth_getBalance_toobig_bn");
    specs.add("eth_getBalance_without_addr");

    specs.add("eth_getBlock_byHash");
    specs.add("eth_getBlock_byHash_InvalidHexBytes32Hash");
    specs.add("eth_getBlock_byHashInvalid");
    specs.add("eth_getBlock_byNumber");
    specs.add("eth_getBlock_byNumberInvalid");
    specs.add("eth_getBlock_wrongParams");

    specs.add("eth_getBlockTransactionCount_byHash");
    specs.add("eth_getBlockTransactionCount_byNumber");

    specs.add("eth_getCode");
    specs.add("eth_getCode_noCode");

    specs.add("eth_getLogs_emptyListParam");
    specs.add("eth_getLogs_matchTopic");
    specs.add("eth_getLogs_matchAnyTopic");
    specs.add("eth_getLogs_range");

    specs.add("eth_getStorageAt");
    specs.add("eth_getStorageAt_illegalRangeGreaterThan");

    specs.add("eth_getTransaction_byBlockHashAndIndex");
    specs.add("eth_getTransaction_byBlockNumberAndIndex");
    specs.add("eth_getTransaction_byBlockNumberAndInvalidIndex");
    specs.add("eth_getTransaction_byHash");
    specs.add("eth_getTransaction_byHashNull");

    specs.add("eth_getTransactionCount");

    specs.add("eth_getTransactionReceipt");

    specs.add("eth_sendRawTransaction_contractCreation");
    specs.add("eth_sendRawTransaction_messageCall");
    specs.add("eth_sendRawTransaction_nonceTooLow");
    specs.add("eth_sendRawTransaction_transferEther");
    specs.add("eth_sendRawTransaction_unsignedTransaction");

    specs.add("eth_syncing");

    specs.add("graphql_blocks_byFrom");
    specs.add("graphql_blocks_byRange");
    specs.add("graphql_blocks_byWrongRange");

    specs.add("graphql_pending");

    specs.add("graphql_tooComplex");
    specs.add("graphql_tooComplexSchema");

    return specs;
  }

  @Test
  public void graphQLCallWithSpecFile() throws Exception {
    graphQLCall(specFileName);
  }

  private void graphQLCall(final String name) throws IOException {
    final String testSpecFile = name + ".json";
    final String json =
        Resources.toString(
            EthGraphQLHttpBySpecTest.class.getResource(testSpecFile), Charsets.UTF_8);
    final JsonObject spec = new JsonObject(json);
    final String rawRequestBody = spec.getString("request");
    final RequestBody requestBody = RequestBody.create(rawRequestBody, GRAPHQL);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    importBlocks(1, BLOCKS.size());
    try (final Response resp = client.newCall(request).execute()) {
      final JsonObject expectedRespBody = spec.getJsonObject("response");
      final String resultStr = resp.body().string();

      final JsonObject result = new JsonObject(resultStr);
      Assertions.assertThat(result).isEqualTo(expectedRespBody);

      final int expectedStatusCode = spec.getInteger("statusCode");
      Assertions.assertThat(resp.code()).isEqualTo(expectedStatusCode);
    }
  }

  private void importBlocks(final int from, final int to) {
    for (int i = from; i < to; ++i) {
      importBlock(i);
    }
  }
}
