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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.FeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistoryResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class EthFeeHistoryTest {
  final BlockDataGenerator gen = new BlockDataGenerator();
  private MutableBlockchain blockchain;
  private EthFeeHistory method;
  private ProtocolSchedule protocolSchedule;

  @Before
  public void setUp() {
    protocolSchedule = mock(ProtocolSchedule.class);
    final Block genesisBlock = gen.genesisBlock();
    blockchain = createInMemoryBlockchain(genesisBlock);
    gen.blockSequence(genesisBlock, 10)
        .forEach(block -> blockchain.appendBlock(block, gen.receipts(block)));
    method = new EthFeeHistory(protocolSchedule, blockchain);
  }

  @Test
  public void params() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    // should fail because no required params given
    assertThatThrownBy(this::feeHistoryRequest).isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> feeHistoryRequest(1)).isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> feeHistoryRequest("latest"))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    feeHistoryRequest(1, "latest");
    // should pass because both required params and optional param given
    feeHistoryRequest(1, "latest", new double[] {1, 20.4});
    // should pass because both required params and optional param given
    feeHistoryRequest("0x1", "latest", new double[] {1, 20.4});
  }

  @Test
  public void allFieldsPresentForLatestBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(eq(11L))).thenReturn(londonSpec);
    assertThat(
            ((JsonRpcSuccessResponse) feeHistoryRequest(1, "latest", new double[] {100.0}))
                .getResult())
        .isEqualTo(
            FeeHistory.FeeHistoryResult.from(
                ImmutableFeeHistory.builder()
                    .oldestBlock(10)
                    .baseFeePerGas(List.of(Wei.of(25496L), Wei.of(28683L)))
                    .gasUsedRatio(List.of(0.9999999992132459))
                    .reward(List.of(List.of(Wei.of(1524763764L))))
                    .build()));
  }

  @Test
  public void cantGetBlockHigherThanChainHead() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    assertThat(((JsonRpcErrorResponse) feeHistoryRequest(2, "11", new double[] {100.0})).getError())
        .isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void blockCountBounds() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    assertThat(
            ((JsonRpcErrorResponse) feeHistoryRequest(0, "latest", new double[] {100.0}))
                .getError())
        .isEqualTo(JsonRpcError.INVALID_PARAMS);
    assertThat(
            ((JsonRpcErrorResponse) feeHistoryRequest(1025, "latest", new double[] {100.0}))
                .getError())
        .isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void doesntGoPastChainHeadWithHighBlockCount() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    final FeeHistory.FeeHistoryResult result =
        (ImmutableFeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest(20, "latest")).getResult();
    assertThat(Long.decode(result.getOldestBlock())).isEqualTo(0);
    assertThat(result.getBaseFeePerGas()).hasSize(12);
    assertThat(result.getGasUsedRatio()).hasSize(11);
    assertThat(result.getReward()).isNull();
  }

  @Test
  public void correctlyHandlesForkBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(11));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    final FeeHistory.FeeHistoryResult result =
        (FeeHistory.FeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest(1, "latest")).getResult();
    assertThat(Wei.fromHexString(result.getBaseFeePerGas().get(1)))
        .isEqualTo(FeeMarket.london(11).getInitialBasefee());
  }

  @Test
  public void allZeroPercentilesForZeroBlock() {
    final ProtocolSpec londonSpec = mock(ProtocolSpec.class);
    when(londonSpec.getFeeMarket()).thenReturn(FeeMarket.london(5));
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(londonSpec);
    final BlockDataGenerator.BlockOptions blockOptions = BlockDataGenerator.BlockOptions.create();
    blockOptions.hasTransactions(false);
    blockOptions.setParentHash(blockchain.getChainHeadHash());
    blockOptions.setBlockNumber(11);
    final Block emptyBlock = gen.block(blockOptions);
    blockchain.appendBlock(emptyBlock, gen.receipts(emptyBlock));
    final FeeHistory.FeeHistoryResult result =
        (FeeHistory.FeeHistoryResult)
            ((JsonRpcSuccessResponse) feeHistoryRequest(1, "latest", new double[] {100.0}))
                .getResult();
    assertThat(result.getReward()).isEqualTo(List.of(List.of("0x0")));
  }

  private JsonRpcResponse feeHistoryRequest(final Object... params) {
    return method.response(
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params)));
  }
}
