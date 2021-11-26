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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ClassicBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RewardTraceGeneratorTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  @Mock private MainnetTransactionProcessor transactionProcessor;

  private final Address ommerBeneficiary =
      Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
  private final Address blockBeneficiary =
      Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d88"));
  private final Wei blockReward = Wei.of(10000);
  private final BlockHeader ommerHeader = gen.header(0x09);
  private final OptionalLong eraRounds = OptionalLong.of(5000000);
  private Block block;

  @Before
  public void setUp() {
    final BlockBody blockBody = new BlockBody(Collections.emptyList(), List.of(ommerHeader));
    final BlockHeader blockHeader =
        gen.header(0x0A, blockBody, new BlockDataGenerator.BlockOptions());
    block = new Block(blockHeader, blockBody);
    when(protocolSchedule.getByBlockNumber(block.getHeader().getNumber())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockReward()).thenReturn(blockReward);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(miningBeneficiaryCalculator);
    when(miningBeneficiaryCalculator.calculateBeneficiary(block.getHeader()))
        .thenReturn(blockBeneficiary);
    when(miningBeneficiaryCalculator.calculateBeneficiary(ommerHeader))
        .thenReturn(ommerBeneficiary);
  }

  @Test
  public void assertThatTraceGeneratorReturnValidRewardsForMainnetBlockProcessor() {
    final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class);
    final MainnetBlockProcessor blockProcessor =
        new MainnetBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            BlockHeader::getCoinbase,
            true,
            Optional.empty());
    when(protocolSpec.getBlockProcessor()).thenReturn(blockProcessor);

    final Stream<Trace> traceStream =
        RewardTraceGenerator.generateFromBlock(protocolSchedule, block);

    final Action.Builder actionBlockReward =
        Action.builder()
            .rewardType("block")
            .author(blockBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getCoinbaseReward(blockReward, block.getHeader().getNumber(), 1)
                    .toShortHexString());
    final Trace blocReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionBlockReward)
            .type("reward")
            .build();

    // calculate reward with MainnetBlockProcessor
    final Action.Builder actionOmmerReward =
        Action.builder()
            .rewardType("uncle")
            .author(ommerBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getOmmerReward(
                        blockReward, block.getHeader().getNumber(), ommerHeader.getNumber())
                    .toShortHexString());
    final Trace ommerReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionOmmerReward)
            .type("reward")
            .build();

    final List<Trace> traces = traceStream.collect(Collectors.toList());

    // check block reward
    assertThat(traces.get(0)).usingRecursiveComparison().isEqualTo(blocReward);
    // check ommer reward
    assertThat(traces.get(1)).usingRecursiveComparison().isEqualTo(ommerReward);
  }

  @Test
  public void assertThatTraceGeneratorReturnValidRewardsForClassicBlockProcessor() {
    final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class);
    final ClassicBlockProcessor blockProcessor =
        new ClassicBlockProcessor(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            BlockHeader::getCoinbase,
            true,
            eraRounds);
    when(protocolSpec.getBlockProcessor()).thenReturn(blockProcessor);

    final Stream<Trace> traceStream =
        RewardTraceGenerator.generateFromBlock(protocolSchedule, block);

    final Action.Builder actionBlockReward =
        Action.builder()
            .rewardType("block")
            .author(blockBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getCoinbaseReward(blockReward, block.getHeader().getNumber(), 1)
                    .toShortHexString());
    final Trace blocReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionBlockReward)
            .type("reward")
            .build();

    // calculate reward with ClassicBlockProcessor
    final Action.Builder actionOmmerReward =
        Action.builder()
            .rewardType("uncle")
            .author(ommerBeneficiary.toHexString())
            .value(
                blockProcessor
                    .getOmmerReward(
                        blockReward, block.getHeader().getNumber(), ommerHeader.getNumber())
                    .toShortHexString());
    final Trace ommerReward =
        new RewardTrace.Builder()
            .blockHash(block.getHash().toHexString())
            .blockNumber(block.getHeader().getNumber())
            .actionBuilder(actionOmmerReward)
            .type("reward")
            .build();

    final List<Trace> traces = traceStream.collect(Collectors.toList());

    // check block reward
    assertThat(traces.get(0)).usingRecursiveComparison().isEqualTo(blocReward);
    // check ommer reward
    assertThat(traces.get(1)).usingRecursiveComparison().isEqualTo(ommerReward);
  }
}
