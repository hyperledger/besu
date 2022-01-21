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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGasPriceTest {

  @Mock private PoWMiningCoordinator miningCoordinator;
  @Mock private Blockchain blockchain;
  private EthGasPrice method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_gasPrice";

  @Before
  public void setUp() {
    method =
        new EthGasPrice(
            new BlockchainQueries(
                blockchain,
                null,
                Optional.empty(),
                Optional.empty(),
                ImmutableApiConfiguration.builder().gasPriceMin(100).build()),
            miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnMinValueWhenNoTransactionsExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x4d2";
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);
    when(miningCoordinator.getMinTransactionGasPrice()).thenReturn(Wei.of(1234));

    when(blockchain.getChainHeadBlockNumber()).thenReturn(1000L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createEmptyBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(miningCoordinator).getMinTransactionGasPrice();
    verifyNoMoreInteractions(miningCoordinator);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(100)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  @Test
  public void shouldReturnMedianWhenTransactionsExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x389fd980"; // 950Wei, gas prices are 900-999 wei.
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);

    when(blockchain.getChainHeadBlockNumber()).thenReturn(1000L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createFakeBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verifyNoMoreInteractions(miningCoordinator);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(100)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  @Test
  public void shortChainQueriesAllBlocks() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x2625a00";
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);

    when(blockchain.getChainHeadBlockNumber()).thenReturn(80L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createFakeBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verifyNoMoreInteractions(miningCoordinator);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(80)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  private Object createFakeBlock(final Long height) {
    return Optional.of(
        new Block(
            new BlockHeader(
                Hash.EMPTY,
                Hash.EMPTY_TRIE_HASH,
                Address.ZERO,
                Hash.EMPTY_TRIE_HASH,
                Hash.EMPTY_TRIE_HASH,
                Hash.EMPTY_TRIE_HASH,
                LogsBloomFilter.builder().build(),
                Difficulty.ONE,
                height,
                0,
                0,
                0,
                Bytes.EMPTY,
                Wei.ZERO,
                Hash.EMPTY,
                0,
                null),
            new BlockBody(
                List.of(
                    new Transaction(
                        0,
                        Wei.of(height * 1000000L),
                        0,
                        Optional.empty(),
                        Wei.ZERO,
                        null,
                        Bytes.EMPTY,
                        Address.ZERO,
                        Optional.empty())),
                List.of())));
  }

  private Object createEmptyBlock(final Long height) {
    return Optional.of(
        new Block(
            new BlockHeader(
                Hash.EMPTY,
                Hash.EMPTY_TRIE_HASH,
                Address.ZERO,
                Hash.EMPTY_TRIE_HASH,
                Hash.EMPTY_TRIE_HASH,
                Hash.EMPTY_TRIE_HASH,
                LogsBloomFilter.builder().build(),
                Difficulty.ONE,
                height,
                0,
                0,
                0,
                Bytes.EMPTY,
                Wei.ZERO,
                Hash.EMPTY,
                0,
                null),
            new BlockBody(List.of(), List.of())));
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }
}
