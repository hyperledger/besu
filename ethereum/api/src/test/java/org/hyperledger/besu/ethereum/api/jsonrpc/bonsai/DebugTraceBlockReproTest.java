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
package org.hyperledger.besu.ethereum.api.jsonrpc.bonsai;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.debug.OpCodeTracerConfig;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.BlockTestUtil.ChainResources;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DebugTraceBlockReproTest {

  private BlockchainSetupUtil blockchainSetupUtil;
  private BlockReplay blockReplay;
  private BlockchainQueries blockchainQueries;

  @BeforeEach
  public void setup() {
    blockchainSetupUtil =
        createBlockchainSetupUtil(
            "/org/hyperledger/besu/ethereum/api/jsonrpc/trace/chain-data/genesis.json",
            "/org/hyperledger/besu/ethereum/api/jsonrpc/trace/chain-data/blocks.bin");
    blockchainSetupUtil.importAllBlocks();
    blockReplay =
        new BlockReplay(
            blockchainSetupUtil.getProtocolSchedule(),
            blockchainSetupUtil.getProtocolContext(),
            blockchainSetupUtil.getBlockchain());
    blockchainQueries =
        new BlockchainQueries(
            blockchainSetupUtil.getProtocolSchedule(),
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive(),
            MiningConfiguration.newDefault());
  }

  private BlockchainSetupUtil createBlockchainSetupUtil(
      final String genesisPath, final String blocksPath) {
    final URL genesisURL = DebugTraceBlockReproTest.class.getResource(genesisPath);
    final URL blocksURL = DebugTraceBlockReproTest.class.getResource(blocksPath);
    return BlockchainSetupUtil.createForEthashChain(
        new ChainResources(genesisURL, blocksURL), DataStorageFormat.BONSAI);
  }

  @Test
  public void storageAppearsInSubsequentTransactions() {
    final Block block = blockchainSetupUtil.getBlockchain().getBlockByNumber(4).orElseThrow();

    final DebugOperationTracer tracer =
        new DebugOperationTracer(new OpCodeTracerConfig(true, false, false), false);
    final BlockTracer blockTracer = new BlockTracer(blockReplay);

    final Optional<BlockTrace> maybeBlockTrace =
        Tracer.processTracing(
            blockchainQueries, block.getHash(), state -> blockTracer.trace(state, block, tracer));

    final BlockTrace blockTrace = maybeBlockTrace.orElseThrow();
    final List<TransactionTrace> transactions = blockTrace.getTransactionTraces();

    final TraceFrame firstTxFirstFrame = transactions.get(0).getTraceFrames().get(0);
    assertThat(firstTxFirstFrame.getStorage()).isEmpty();

    final TraceFrame secondTxFirstFrame = transactions.get(1).getTraceFrames().get(0);
    System.out.println("Second tx first frame: " + secondTxFirstFrame.getStorage());
    assertThat(secondTxFirstFrame.getStorage()).isEmpty();
  }
}
