/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;

import java.math.BigInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthConfigTest {

  private EthConfig method;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private GenesisConfigOptions genesisConfigOptions;
  protected BlockchainSetupUtil blockchainSetupUtil;

  @BeforeEach
  void setup() {
    blockchainSetupUtil = BlockchainSetupUtil.forMainnet();
    when(blockchainQueries.getBlockchain()).thenReturn(blockchainSetupUtil.getBlockchain());
    method =
        new EthConfig(
            blockchainQueries, blockchainSetupUtil.getProtocolSchedule(), genesisConfigOptions);
  }

  @Test
  void ethConfigForMainnet() {
    final JsonRpcSuccessResponse res =
        ((JsonRpcSuccessResponse)
            method.response(
                new JsonRpcRequestContext(
                    new JsonRpcRequest("2.0", "eth_config", new Object[] {true}))));
    final ObjectNode result = (ObjectNode) res.getResult();
    assertThat(result.has("all")).isTrue();
    assertThat(result.get("all").size()).isGreaterThan(3);
    for (JsonNode forkObj : result.get("all")) {
      assertThat(forkObj.get("chainId").asText()).isEqualTo("0x" + BigInteger.ONE.toString(16));
    }
    System.out.println(result);
  }
}
