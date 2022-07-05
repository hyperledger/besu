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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByNumberTest {
  private static final String JSON_RPC_VERSION = "2.0";
  private static final String ETH_METHOD = "eth_getBlockByNumber";
  private static final int BLOCKCHAIN_LENGTH = 4;
  private static final int FINALIZED_BLOCK_HEIGHT = 1;
  private static final int SAFE_BLOCK_HEIGHT = 2;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  private final BlockResultFactory blockResult = new BlockResultFactory();
  private BlockchainQueries blockchainQueries;
  private MutableBlockchain blockchain;
  private EthGetBlockByNumber method;
  @Mock private Synchronizer synchronizer;
  @Mock private WorldStateArchive worldStateArchive;

  @Before
  public void setUp() {
    blockchain = createInMemoryBlockchain(blockDataGenerator.genesisBlock());

    for (int i = 1; i < BLOCKCHAIN_LENGTH; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(blockchain.getBlockHashByNumber(i - 1).orElseThrow());
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);

      blockchain.appendBlock(block, receipts);
    }

    BlockHeader lastestHeader = blockchain.getChainHeadBlock().getHeader();
    when(worldStateArchive.isWorldStateAvailable(
            lastestHeader.getStateRoot(), lastestHeader.getHash()))
        .thenReturn(Boolean.TRUE);

    blockchainQueries = spy(new BlockchainQueries(blockchain, worldStateArchive));

    method = new EthGetBlockByNumber(blockchainQueries, blockResult, synchronizer);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void exceptionWhenNoParamsSupplied() {
    assertThatThrownBy(() -> method.response(requestWithParams()))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenNoNumberSupplied() {
    assertThatThrownBy(() -> method.response(requestWithParams("false")))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenNoBoolSupplied() {
    assertThatThrownBy(() -> method.response(requestWithParams("0")))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 1");
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenNumberParamInvalid() {
    assertThatThrownBy(() -> method.response(requestWithParams("invalid", "true")))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 0");
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void exceptionWhenBoolParamInvalid() {
    assertThatThrownBy(() -> method.response(requestWithParams("0", "maybe")))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 1");
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void errorWhenAskingFinalizedButFinalizedIsNotPresent() {
    JsonRpcResponse resp = method.response(requestWithParams("finalized", "false"));
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    JsonRpcErrorResponse errorResp = (JsonRpcErrorResponse) resp;
    assertThat(errorResp.getError()).isEqualTo(JsonRpcError.UNKNOWN_BLOCK);
  }

  @Test
  public void errorWhenAskingSafeButSafeIsNotPresent() {
    JsonRpcResponse resp = method.response(requestWithParams("safe", "false"));
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    JsonRpcErrorResponse errorResp = (JsonRpcErrorResponse) resp;
    assertThat(errorResp.getError()).isEqualTo(JsonRpcError.UNKNOWN_BLOCK);
  }

  @Test
  public void successWhenAskingEarliest() {
    assertSuccess("earliest", 0L);
  }

  @Test
  public void successWhenAskingLatest() {
    assertSuccess("latest", BLOCKCHAIN_LENGTH - 1);
  }

  @Test
  public void successWhenAskingFinalized() {
    assertSuccessPos("finalized", FINALIZED_BLOCK_HEIGHT);
  }

  @Test
  public void successWhenAskingSafe() {
    assertSuccessPos("safe", SAFE_BLOCK_HEIGHT);
  }

  private void assertSuccess(final String tag, final long height) {
    JsonRpcResponse resp = method.response(requestWithParams(tag, "false"));
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    JsonRpcSuccessResponse successResp = (JsonRpcSuccessResponse) resp;
    BlockResult blockResult = (BlockResult) successResp.getResult();
    assertThat(blockResult.getHash())
        .isEqualTo(blockchain.getBlockHashByNumber(height).get().toString());
  }

  private void assertSuccessPos(final String tag, final long height) {
    blockchain.setSafeBlock(blockchain.getBlockByNumber(SAFE_BLOCK_HEIGHT).get().getHash());
    blockchain.setFinalized(blockchain.getBlockByNumber(FINALIZED_BLOCK_HEIGHT).get().getHash());
    assertSuccess(tag, height);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }
}
