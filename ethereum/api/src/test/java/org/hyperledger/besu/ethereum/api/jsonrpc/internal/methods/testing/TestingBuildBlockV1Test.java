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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestingBuildBlockV1Test {

  @Mock private ProtocolContext protocolContext;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private MiningConfiguration miningConfiguration;
  @Mock private TransactionPool transactionPool;
  @Mock private EthScheduler ethScheduler;
  @Mock private MutableBlockchain blockchain;

  private TestingBuildBlockV1 method;

  @BeforeEach
  void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    method =
        new TestingBuildBlockV1(
            protocolContext, protocolSchedule, miningConfiguration, transactionPool, ethScheduler);
  }

  @Test
  void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("testing_buildBlockV1");
  }

  @Test
  void shouldReturnErrorWhenParentBlockNotFound() {
    when(blockchain.getBlockHeader(any(Hash.class))).thenReturn(Optional.empty());

    final JsonRpcResponse response =
        method.response(requestWithParentHash(Hash.ZERO.toHexString()));

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError().getCode())
        .isEqualTo(RpcErrorType.INVALID_BLOCK_HASH_PARAMS.getCode());
  }

  @Test
  void shouldReturnErrorForInvalidTransactionRlp() {
    final BlockHeader parentHeader = new BlockHeaderTestFixture().buildHeader();
    when(blockchain.getBlockHeader(any(Hash.class))).thenReturn(Optional.of(parentHeader));

    final JsonRpcResponse response =
        method.response(
            requestWithTransactions(parentHeader.getHash().toHexString(), List.of("0xINVALIDRLP")));

    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError().getCode()).isEqualTo(RpcErrorType.INVALID_PARAMS.getCode());
  }

  private JsonRpcRequestContext requestWithParentHash(final String parentHash) {
    return requestWithTransactions(parentHash, Collections.emptyList());
  }

  private JsonRpcRequestContext requestWithTransactions(
      final String parentHash, final List<String> transactions) {
    final Map<String, Object> payloadAttributes = new LinkedHashMap<>();
    payloadAttributes.put("timestamp", "0x1");
    payloadAttributes.put("prevRandao", Hash.ZERO.toHexString());
    payloadAttributes.put("suggestedFeeRecipient", "0x0000000000000000000000000000000000000000");
    payloadAttributes.put("withdrawals", Collections.emptyList());

    final Map<String, Object> param = new LinkedHashMap<>();
    param.put("parentBlockHash", parentHash);
    param.put("payloadAttributes", payloadAttributes);
    param.put("transactions", transactions);

    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "testing_buildBlockV1", new Object[] {param}));
  }
}
