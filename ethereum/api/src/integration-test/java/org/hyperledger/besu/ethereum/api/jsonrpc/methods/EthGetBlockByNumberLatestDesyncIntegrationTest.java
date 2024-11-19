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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.util.Optional;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class EthGetBlockByNumberLatestDesyncIntegrationTest {

  private static JsonRpcTestMethodsFactory methodsFactorySynced;
  private static JsonRpcTestMethodsFactory methodsFactoryDesynced;
  private static JsonRpcTestMethodsFactory methodsFactoryMidDownload;
  private static final long ARBITRARY_SYNC_BLOCK = 4L;

  @BeforeAll
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);
    BlockchainImporter importer =
        new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson);
    MutableBlockchain chain =
        InMemoryKeyValueStorageProvider.createInMemoryBlockchain(importer.getGenesisBlock());
    WorldStateArchive state = InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive();
    importer.getGenesisState().writeStateTo(state.getMutable());
    ProtocolContext context =
        new ProtocolContext(chain, state, mock(ConsensusContext.class), new BadBlockManager());

    for (final Block block : importer.getBlocks()) {
      final ProtocolSchedule protocolSchedule = importer.getProtocolSchedule();
      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(block.getHeader());
      final BlockImporter blockImporter = protocolSpec.getBlockImporter();
      blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
    }

    methodsFactorySynced = new JsonRpcTestMethodsFactory(importer, chain, state, context);

    WorldStateArchive unsynced = mock(WorldStateArchive.class);
    when(unsynced.isWorldStateAvailable(any(Hash.class), any(Hash.class))).thenReturn(false);

    methodsFactoryDesynced = new JsonRpcTestMethodsFactory(importer, chain, unsynced, context);

    WorldStateArchive midSync = mock(WorldStateArchive.class);
    when(midSync.isWorldStateAvailable(any(Hash.class), any(Hash.class))).thenReturn(true);

    Synchronizer synchronizer = mock(Synchronizer.class);
    SyncStatus status = mock(SyncStatus.class);
    when(status.getCurrentBlock())
        .thenReturn(ARBITRARY_SYNC_BLOCK); // random choice for current sync state.
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(status));

    methodsFactoryMidDownload =
        new JsonRpcTestMethodsFactory(importer, chain, midSync, context, synchronizer);
  }

  @Test
  public void shouldReturnHeadIfFullySynced() {
    JsonRpcMethod ethGetBlockNumber = methodsFactorySynced.methods().get("eth_getBlockByNumber");
    Object[] params = {"latest", false};
    JsonRpcRequestContext ctx =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_getBlockByNumber", params));
    Assertions.assertThatNoException()
        .isThrownBy(
            () -> {
              final JsonRpcResponse resp = ethGetBlockNumber.response(ctx);
              assertThat(resp).isNotNull();
              assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
              Object r = ((JsonRpcSuccessResponse) resp).getResult();
              assertThat(r).isInstanceOf(BlockResult.class);
              BlockResult br = (BlockResult) r;
              assertThat(br.getNumber()).isEqualTo("0x20");
              // assert on the state existing?
            });
  }

  @Test
  public void shouldReturnGenesisIfNotSynced() {

    JsonRpcMethod ethGetBlockNumber = methodsFactoryDesynced.methods().get("eth_getBlockByNumber");
    Object[] params = {"latest", false};
    JsonRpcRequestContext ctx =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_getBlockByNumber", params));
    Assertions.assertThatNoException()
        .isThrownBy(
            () -> {
              final JsonRpcResponse resp = ethGetBlockNumber.response(ctx);
              assertThat(resp).isNotNull();
              assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
              Object r = ((JsonRpcSuccessResponse) resp).getResult();
              assertThat(r).isInstanceOf(BlockResult.class);
              BlockResult br = (BlockResult) r;
              assertThat(br.getNumber()).isEqualTo("0x0");
            });
  }

  @Test
  public void shouldReturnCurrentSyncedIfDownloadingWorldState() {
    JsonRpcMethod ethGetBlockNumber =
        methodsFactoryMidDownload.methods().get("eth_getBlockByNumber");
    Object[] params = {"latest", false};
    JsonRpcRequestContext ctx =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_getBlockByNumber", params));
    Assertions.assertThatNoException()
        .isThrownBy(
            () -> {
              final JsonRpcResponse resp = ethGetBlockNumber.response(ctx);
              assertThat(resp).isNotNull();
              assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
              Object r = ((JsonRpcSuccessResponse) resp).getResult();
              assertThat(r).isInstanceOf(BlockResult.class);
              BlockResult br = (BlockResult) r;
              assertThat(br.getNumber()).isEqualTo("0x4");
            });
  }
}
