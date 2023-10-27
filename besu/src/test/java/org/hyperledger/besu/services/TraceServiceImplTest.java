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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockTraceResult;
import org.hyperledger.besu.plugin.data.TransactionTraceResult;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
class TraceServiceImplTest {

  TraceService traceService;
  private MutableBlockchain blockchain;
  private WorldStateArchive worldStateArchive;
  private BlockchainQueries blockchainQueries;

  /**
   * The blockchain for testing has a height of 32 and the account
   * 0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b makes a transaction per block. So, in the head, the
   * nonce of this account should be 32.
   */
  @BeforeEach
  public void setup() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    worldStateArchive = blockchainSetupUtil.getWorldArchive();
    blockchainQueries = new BlockchainQueries(blockchain, worldStateArchive);
    traceService =
        new TraceServiceImpl(blockchainQueries, blockchainSetupUtil.getProtocolSchedule());
  }

  @Test
  void shouldRetrieveStateUpdatePostTracingForOneBlock() {

    final Address addressToVerify =
        Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    final long persistedNonceForAccount =
        worldStateArchive.getMutable().get(addressToVerify).getNonce();

    traceService.trace(
        2,
        2,
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(1);
        },
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(2);
        },
        BlockAwareOperationTracer.NO_TRACING);

    assertThat(worldStateArchive.getMutable().get(addressToVerify).getNonce())
        .isEqualTo(persistedNonceForAccount);
  }

  @Test
  void shouldRetrieveStateUpdatePostTracingForAllBlocks() {
    final Address addressToVerify =
        Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    final long persistedNonceForAccount =
        worldStateArchive.getMutable().get(addressToVerify).getNonce();

    traceService.trace(
        0,
        32,
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(0);
        },
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce())
              .isEqualTo(persistedNonceForAccount);
        },
        BlockAwareOperationTracer.NO_TRACING);

    assertThat(worldStateArchive.getMutable().get(addressToVerify).getNonce())
        .isEqualTo(persistedNonceForAccount);
  }

  @Test
  void shouldReturnTheCorrectWorldViewForTxStartEnd() {
    final TxStartEndTracer txStartEndTracer = new TxStartEndTracer();

    // block contains 1 transaction
    final BlockTraceResult blockTraceResult = traceService.traceBlock(31, txStartEndTracer);

    assertThat(blockTraceResult).isNotNull();

    final List<TransactionTraceResult> transactionTraceResults =
        blockTraceResult.transactionTraceResults();
    assertThat(transactionTraceResults.size()).isEqualTo(1);

    assertThat(transactionTraceResults.get(0).getTxHash()).isNotNull();
    assertThat(transactionTraceResults.get(0).getStatus())
        .isEqualTo(TransactionTraceResult.Status.SUCCESS);
    assertThat(transactionTraceResults.get(0).errorMessage()).isEmpty();

    assertThat(txStartEndTracer.txStartWorldView).isNotNull();
    assertThat(txStartEndTracer.txEndWorldView).isNotNull();

    assertThat(txStartEndTracer.txStartTransaction.getNonce())
        .isEqualTo(txStartEndTracer.txEndTransaction.getNonce())
        .isEqualTo(30);
    assertThat(txStartEndTracer.txStartTransaction.getGasLimit())
        .isEqualTo(txStartEndTracer.txEndTransaction.getGasLimit())
        .isEqualTo(314159);
    assertThat(txStartEndTracer.txStartTransaction.getTo().get())
        .isEqualTo(txStartEndTracer.txEndTransaction.getTo().get())
        .isEqualTo(Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"));
    assertThat(txStartEndTracer.txStartTransaction.getValue())
        .isEqualTo(txStartEndTracer.txEndTransaction.getValue())
        .isEqualTo(
            Wei.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000000a"));
    assertThat(txStartEndTracer.txStartTransaction.getPayload())
        .isEqualTo(txStartEndTracer.txEndTransaction.getPayload())
        .isEqualTo(Bytes.fromHexString("0xfd408767"));

    assertThat(txStartEndTracer.txEndStatus).isTrue();
    assertThat(txStartEndTracer.txEndOutput).isEqualTo(Bytes.fromHexString("0x"));
    assertThat(txStartEndTracer.txEndGasUsed).isEqualTo(24303);
    assertThat(txStartEndTracer.txEndTimeNs).isNotNull();

    assertThat(txStartEndTracer.txEndLogs).isNotEmpty();

    final Log actualLog = txStartEndTracer.txEndLogs.get(0);
    assertThat(actualLog.getLogger())
        .isEqualTo(Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f"));
    assertThat(actualLog.getData())
        .isEqualTo(
            Bytes.fromHexString(
                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe9000000000000000000000000000000000000000000000000000000000000002a"));
    assertThat(actualLog.getTopics()).hasSize(4);
    assertThat(actualLog.getTopics().get(0))
        .isEqualTo(
            Bytes.fromHexString(
                "0xd5f0a30e4be0c6be577a71eceb7464245a796a7e6a55c0d971837b250de05f4e"));
    assertThat(actualLog.getTopics().get(1))
        .isEqualTo(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"));
    assertThat(actualLog.getTopics().get(2))
        .isEqualTo(
            Bytes.fromHexString(
                "0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
    assertThat(actualLog.getTopics().get(3))
        .isEqualTo(
            Bytes.fromHexString(
                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
  }

  private static class TxStartEndTracer implements BlockAwareOperationTracer {
    public WorldView txStartWorldView;
    public WorldView txEndWorldView;

    public Transaction txStartTransaction;
    public Transaction txEndTransaction;

    public boolean txEndStatus;
    public Bytes txEndOutput;
    public List<Log> txEndLogs;
    public long txEndGasUsed;
    public Long txEndTimeNs;

    @Override
    public void traceStartTransaction(final WorldView worldView, final Transaction transaction) {
      txStartWorldView = worldView;
      txStartTransaction = transaction;
    }

    @Override
    public void traceEndTransaction(
        final WorldView worldView,
        final Transaction transaction,
        final boolean status,
        final Bytes output,
        final List<Log> logs,
        final long gasUsed,
        final long timeNs) {
      txEndWorldView = worldView;
      txEndTransaction = transaction;
      txEndStatus = status;
      txEndOutput = output;
      txEndLogs = logs;
      txEndGasUsed = gasUsed;
      txEndTimeNs = timeNs;
    }
  }
}
