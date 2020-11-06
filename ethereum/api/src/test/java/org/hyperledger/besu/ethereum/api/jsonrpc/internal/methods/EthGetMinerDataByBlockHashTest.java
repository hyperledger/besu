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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.WORLD_STATE_UNAVAILABLE;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.Optional;

import com.google.common.base.Suppliers;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetMinerDataByBlockHashTest {
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private Blockchain blockChain;
  private EthGetMinerDataByBlockHash method;
  private final String ETH_METHOD = "eth_getMinerDataByBlockHash";
  private final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();

  @Before
  public void before() {
    this.method =
        new EthGetMinerDataByBlockHash(Suppliers.ofInstance(blockchainQueries), protocolSchedule);
  }

  @Test
  public void shouldReturnExpectedMethodNameTest() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void successTest() {
    final BlockHeader header = blockHeaderTestFixture.buildHeader();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        new BlockWithMetadata<>(
            header, Collections.emptyList(), Collections.emptyList(), Difficulty.of(100L), 5);

    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);
    Mockito.when(blockchainQueries.getWorldStateArchive().isWorldStateAvailable(Mockito.any()))
        .thenReturn(true);
    Mockito.when(protocolSchedule.getByBlockNumber(header.getNumber())).thenReturn(protocolSpec);
    Mockito.when(protocolSpec.getBlockReward()).thenReturn(Wei.fromEth(2));
    Mockito.when(blockchainQueries.getBlockchain()).thenReturn(blockChain);

    JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0",
            ETH_METHOD,
            Arrays.array("0x1349e5d4002e72615ae371dc173ba530bf98a7bef886d5b3b00ca5f217565039"));
    JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    JsonRpcResponse response = method.response(requestContext);

    Assertions.assertThat(response).isNotNull().isInstanceOf(JsonRpcSuccessResponse.class);
    Assertions.assertThat(((JsonRpcSuccessResponse) response).getResult()).isNotNull();
    Assertions.assertThat(((JsonRpcSuccessResponse) response).getResult())
        .hasFieldOrProperty("netBlockReward")
        .hasFieldOrProperty("staticBlockReward")
        .hasFieldOrProperty("transactionFee")
        .hasFieldOrProperty("uncleInclusionReward")
        .hasFieldOrProperty("uncleRewards")
        .hasFieldOrProperty("coinbase")
        .hasFieldOrProperty("extraData")
        .hasFieldOrProperty("difficulty")
        .hasFieldOrProperty("totalDifficulty");
  }

  @Test
  public void worldStateMissingTest() {
    final BlockHeader header = blockHeaderTestFixture.buildHeader();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        new BlockWithMetadata<>(
            header, Collections.emptyList(), Collections.emptyList(), Difficulty.of(100L), 5);

    Mockito.when(blockchainQueries.blockByHash(Mockito.any()))
        .thenReturn(Optional.of(blockWithMetadata));
    Mockito.when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);
    Mockito.when(blockchainQueries.getWorldStateArchive().isWorldStateAvailable(Mockito.any()))
        .thenReturn(false);

    JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0",
            ETH_METHOD,
            Arrays.array("0x1349e5d4002e72615ae371dc173ba530bf98a7bef886d5b3b00ca5f217565039"));
    JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    JsonRpcResponse response = method.response(requestContext);

    Assertions.assertThat(response).isNotNull().isInstanceOf(JsonRpcErrorResponse.class);
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError()).isNotNull();
    Assertions.assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualTo(WORLD_STATE_UNAVAILABLE);
  }

  @Test
  public void exceptionWhenNoHashSuppliedTest() {
    JsonRpcRequest request = new JsonRpcRequest("2.0", ETH_METHOD, Arrays.array());
    JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    Assertions.assertThatThrownBy(() -> method.response(requestContext))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");

    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenHashParamInvalidTest() {
    JsonRpcRequest request = new JsonRpcRequest("2.0", ETH_METHOD, Arrays.array("hash"));
    JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    Assertions.assertThatThrownBy(() -> method.response(requestContext))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 0");

    verifyNoMoreInteractions(blockchainQueries);
  }
}
