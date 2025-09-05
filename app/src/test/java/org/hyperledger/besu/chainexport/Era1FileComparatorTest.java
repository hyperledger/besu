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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.era1.Era1Accumulator;
import org.hyperledger.besu.util.era1.Era1BlockIndex;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockBody;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockHeader;
import org.hyperledger.besu.util.era1.Era1ExecutionBlockReceipts;
import org.hyperledger.besu.util.era1.Era1Reader;
import org.hyperledger.besu.util.era1.Era1TotalDifficulty;
import org.hyperledger.besu.util.io.InputStreamFactory;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled // not intended to be run during regular testing, this test is for manual testing only
public class Era1FileComparatorTest {

  @Test
  public void compareFiles() throws IOException {
    final String actualFile = "/home/matilda/sepolia-00000-93375bd8.era1";
    final String expectedFile = "/home/matilda/sepolia-00000-643a00f7.era1";

    final SnappyFactory snappyFactory = new SnappyFactory();
    final InputStreamFactory inputStreamFactory = new InputStreamFactory();

    final Era1Reader expectedEra1Reader = new Era1Reader(snappyFactory, inputStreamFactory);
    final Era1ReaderListener expectedListener = new Era1ReaderListener();
    expectedEra1Reader.read(new FileInputStream(expectedFile), expectedListener);

    final Era1Reader actualEra1Reader = new Era1Reader(snappyFactory, inputStreamFactory);
    final Era1ReaderListener actualListener = new Era1ReaderListener();
    actualEra1Reader.read(new FileInputStream(actualFile), actualListener);

    Assertions.assertEquals(
        expectedListener.getBlockHeaders().size(), actualListener.getBlockHeaders().size());
    for (int i = 0; i < expectedListener.getBlockHeaderRlpLength().size(); i++) {
      Assertions.assertEquals(
          expectedListener.getBlockHeaderRlpLength().get(i),
          actualListener.getBlockHeaderRlpLength().get(i),
          "Header RLP lengths for block " + i + " do not match");
      Assertions.assertEquals(
          expectedListener.getBlockHeaders().get(i),
          actualListener.getBlockHeaders().get(i),
          "Headers for block " + i + " do not match");
    }

    Assertions.assertEquals(
        expectedListener.getBlockBodies().size(), actualListener.getBlockBodies().size());
    for (int i = 0; i < expectedListener.getBlockBodiesRlpLength().size(); i++) {
      Assertions.assertEquals(
          expectedListener.getBlockBodiesRlpLength().get(i),
          actualListener.getBlockBodiesRlpLength().get(i),
          "Body RLP lengths for block " + i + " do not match");
      Assertions.assertEquals(
          expectedListener.getBlockBodies().get(i),
          actualListener.getBlockBodies().get(i),
          "Body for block " + i + " do not match");
    }

    Assertions.assertEquals(
        expectedListener.getTransactionReceiptLists().size(),
        actualListener.getTransactionReceiptLists().size());
    for (int i = 0; i < expectedListener.getTransactionReceiptsRlpLength().size(); i++) {
      Assertions.assertEquals(
          expectedListener.getTransactionReceiptLists().get(i).size(),
          actualListener.getTransactionReceiptLists().get(i).size(),
          "Transaction Receipt counts for block " + i + " do not match");
      Assertions.assertEquals(
          expectedListener.getTransactionReceiptsRlpLength().get(i),
          actualListener.getTransactionReceiptsRlpLength().get(i),
          "Transaction Receipts for block " + i + " do not match");
    }

    Assertions.assertEquals(
        expectedListener.getDifficulties().size(), actualListener.getDifficulties().size());
    for (int i = 0; i < expectedListener.getDifficulties().size(); i++) {
      Assertions.assertArrayEquals(
          expectedListener.getDifficulties().get(i).totalDifficulty(),
          actualListener.getDifficulties().get(i).totalDifficulty(),
          "Total Difficulties for block " + i + " do not match");
    }

    Assertions.assertEquals(
        expectedListener.getAccumulators().size(), actualListener.getAccumulators().size());
    Assertions.assertEquals(1, expectedListener.getAccumulators().size());
    Assertions.assertEquals(
        Bytes.wrap(expectedListener.getAccumulators().get(0).accumulator()).toHexString(),
        Bytes.wrap(actualListener.getAccumulators().get(0).accumulator()).toHexString());

    Assertions.assertEquals(
        expectedListener.getBlockIndexes().size(), actualListener.getBlockIndexes().size());
    Assertions.assertEquals(1, expectedListener.getBlockIndexes().size());
  }

  private static class Era1ReaderListener
      implements org.hyperledger.besu.util.era1.Era1ReaderListener {
    private final BlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();

    private final List<BlockHeader> blockHeaders = new ArrayList<>();
    private final List<Integer> blockHeaderRlpLength = new ArrayList<>();
    private final List<BlockBody> blockBodies = new ArrayList<>();
    private final List<Integer> blockBodiesRlpLength = new ArrayList<>();
    private final List<List<TransactionReceipt>> transactionReceiptLists = new ArrayList<>();
    private final List<Integer> transactionReceiptsRlpLength = new ArrayList<>();
    private final List<Era1BlockIndex> blockIndexes = new ArrayList<>();
    private final List<Era1Accumulator> accumulators = new ArrayList<>();
    private final List<Era1TotalDifficulty> difficulties = new ArrayList<>();

    @Override
    public void handleExecutionBlockHeader(final Era1ExecutionBlockHeader executionBlockHeader) {
      blockHeaderRlpLength.add(executionBlockHeader.header().length);
      blockHeaders.add(
          BlockHeader.readFrom(
              new BytesValueRLPInput(Bytes.wrap(executionBlockHeader.header()), false),
              blockHeaderFunctions));
    }

    @Override
    public void handleExecutionBlockBody(final Era1ExecutionBlockBody executionBlockBody) {
      blockBodiesRlpLength.add(executionBlockBody.block().length);
      blockBodies.add(
          BlockBody.readWrappedBodyFrom(
              new BytesValueRLPInput(Bytes.wrap(executionBlockBody.block()), false),
              blockHeaderFunctions,
              true));
    }

    @Override
    public void handleExecutionBlockReceipts(
        final Era1ExecutionBlockReceipts executionBlockReceipts) {
      transactionReceiptsRlpLength.add(executionBlockReceipts.receipts().length);
      RLPInput rlpInput =
          new BytesValueRLPInput(Bytes.wrap(executionBlockReceipts.receipts()), false);
      if (rlpInput.nextIsList()) {
        rlpInput.enterList();
        List<TransactionReceipt> receipts = new ArrayList<>();
        while (rlpInput.nextIsList()) {
          receipts.add(TransactionReceiptDecoder.readFrom(rlpInput.readAsRlp(), true));
        }
        rlpInput.leaveListLenient();
        transactionReceiptLists.add(receipts);
      } else {
        transactionReceiptLists.add(new ArrayList<>());
      }
    }

    @Override
    public void handleBlockIndex(final Era1BlockIndex blockIndex) {
      blockIndexes.add(blockIndex);
    }

    @Override
    public void handleAccumulator(final Era1Accumulator era1Accumulator) {
      accumulators.add(era1Accumulator);
    }

    @Override
    public void handleTotalDifficulty(final Era1TotalDifficulty era1TotalDifficulty) {
      difficulties.add(era1TotalDifficulty);
    }

    public List<BlockHeader> getBlockHeaders() {
      return blockHeaders;
    }

    public List<BlockBody> getBlockBodies() {
      return blockBodies;
    }

    public List<List<TransactionReceipt>> getTransactionReceiptLists() {
      return transactionReceiptLists;
    }

    public List<Era1BlockIndex> getBlockIndexes() {
      return blockIndexes;
    }

    public List<Era1Accumulator> getAccumulators() {
      return accumulators;
    }

    public List<Era1TotalDifficulty> getDifficulties() {
      return difficulties;
    }

    public List<Integer> getBlockHeaderRlpLength() {
      return blockHeaderRlpLength;
    }

    public List<Integer> getBlockBodiesRlpLength() {
      return blockBodiesRlpLength;
    }

    public List<Integer> getTransactionReceiptsRlpLength() {
      return transactionReceiptsRlpLength;
    }
  }
}
