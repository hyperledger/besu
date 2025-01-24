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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.consensus.merge.TransitionProtocolSchedule;
import org.hyperledger.besu.consensus.merge.TransitionUtils;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MilestoneStreamingProtocolSchedule;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.forkid.ForkId;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ForkIdsNetworkConfigTest {

  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[] {
          NetworkName.SEPOLIA,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xfe3366e7L), 1735371L),
              new ForkId(Bytes.ofUnsignedInt(0xb96cbd13L), 1677557088L),
              new ForkId(Bytes.ofUnsignedInt(0xf7f9bc08L), 1706655072L),
              new ForkId(Bytes.ofUnsignedInt(0x88cf81d9L), 1739980128L),
              new ForkId(Bytes.ofUnsignedInt(0xbafd09c3L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xbafd09c3L), 0L))
        },
        new Object[] {
          NetworkName.HOLESKY,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xc61a6098L), 1696000704L),
              new ForkId(Bytes.ofUnsignedInt(0xfd4f016bL), 1707305664L),
              new ForkId(Bytes.ofUnsignedInt(0x9b192ad0L), 1739352768L),
              new ForkId(Bytes.ofUnsignedInt(0xf818a0d6L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xf818a0d6L), 0L))
        },
        new Object[] {
          NetworkName.MAINNET,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xfc64ec04L), 1150000L),
              new ForkId(Bytes.ofUnsignedInt(0x97c2c34cL), 1920000L),
              new ForkId(Bytes.ofUnsignedInt(0x91d1f948L), 2463000L),
              new ForkId(Bytes.ofUnsignedInt(0x91d1f948L), 2463000L),
              new ForkId(Bytes.ofUnsignedInt(0x91d1f948L), 2463000L),
              new ForkId(Bytes.ofUnsignedInt(0x7a64da13L), 2675000L),
              new ForkId(Bytes.ofUnsignedInt(0x3edd5b10L), 4370000L),
              new ForkId(Bytes.ofUnsignedInt(0xa00bc324L), 7280000L),
              new ForkId(Bytes.ofUnsignedInt(0x668db0afL), 9069000L),
              new ForkId(Bytes.ofUnsignedInt(0x879d6e30L), 9200000L),
              new ForkId(Bytes.ofUnsignedInt(0xe029e991L), 12244000L),
              new ForkId(Bytes.ofUnsignedInt(0xeb440f6L), 12965000L),
              new ForkId(Bytes.ofUnsignedInt(0xb715077dL), 13773000L),
              new ForkId(Bytes.ofUnsignedInt(0x20c327fcL), 15050000L),
              new ForkId(Bytes.ofUnsignedInt(0xf0afd0e3L), 1681338455L),
              new ForkId(Bytes.ofUnsignedInt(0xdce96c2dL), 1710338135L),
              new ForkId(Bytes.ofUnsignedInt(0x9f3d2254L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x9f3d2254L), 0L))
        },
        new Object[] {
          NetworkName.MORDOR,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0x175782aaL), 301243L),
              new ForkId(Bytes.ofUnsignedInt(0x604f6ee1L), 999983L),
              new ForkId(Bytes.ofUnsignedInt(0xf42f5539L), 2520000L),
              new ForkId(Bytes.ofUnsignedInt(0x66b5c286L), 3985893),
              new ForkId(Bytes.ofUnsignedInt(0x92b323e0L), 5520000L),
              new ForkId(Bytes.ofUnsignedInt(0x8c9b1797L), 9957000L),
              new ForkId(Bytes.ofUnsignedInt(0x3a6b00d7L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x3a6b00d7L), 0L))
        },
        new Object[] {
          NetworkName.CLASSIC,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xfc64ec04L), 1150000L),
              new ForkId(Bytes.ofUnsignedInt(0x97c2c34cL), 2500000L),
              new ForkId(Bytes.ofUnsignedInt(0x97c2c34cL), 2500000L),
              new ForkId(Bytes.ofUnsignedInt(0x97c2c34cL), 2500000L),
              new ForkId(Bytes.ofUnsignedInt(0xdb06803fL), 3000000L),
              new ForkId(Bytes.ofUnsignedInt(0xaff4bed4L), 5000000L),
              new ForkId(Bytes.ofUnsignedInt(0xf79a63c0L), 5900000L),
              new ForkId(Bytes.ofUnsignedInt(0x744899d6L), 8772000L),
              new ForkId(Bytes.ofUnsignedInt(0x518b59c6L), 9573000L),
              new ForkId(Bytes.ofUnsignedInt(0x7ba22882L), 10500839L),
              new ForkId(Bytes.ofUnsignedInt(0x9007bfccL), 11700000L),
              new ForkId(Bytes.ofUnsignedInt(0xdb63a1caL), 13189133),
              new ForkId(Bytes.ofUnsignedInt(0x0f6bf187L), 14525000L),
              new ForkId(Bytes.ofUnsignedInt(0x7fd1bb25L), 19250000L),
              new ForkId(Bytes.ofUnsignedInt(0xbe46d57cL), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xbe46d57cL), 0L))
        });
  }

  @ParameterizedTest
  @MethodSource("parameters")
  public void testForkId(final NetworkName chainName, final List<ForkId> expectedForkIds) {
    final GenesisConfig genesisConfig = GenesisConfig.fromResource(chainName.getGenesisFile());
    final MilestoneStreamingTransitionProtocolSchedule schedule = createSchedule(genesisConfig);
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, schedule);
    final Blockchain mockBlockchain = mock(Blockchain.class);
    final BlockHeader mockBlockHeader = mock(BlockHeader.class);

    when(mockBlockchain.getGenesisBlock()).thenReturn(genesisState.getBlock());

    final AtomicLong blockNumber = new AtomicLong();
    when(mockBlockchain.getChainHeadHeader()).thenReturn(mockBlockHeader);
    lenient().when(mockBlockHeader.getNumber()).thenAnswer(o -> blockNumber.get());
    lenient().when(mockBlockHeader.getTimestamp()).thenAnswer(o -> blockNumber.get());

    final ForkIdManager forkIdManager =
        new ForkIdManager(
            mockBlockchain,
            genesisConfig.getForkBlockNumbers(),
            genesisConfig.getForkTimestamps(),
            false);

    final List<ForkId> actualForkIds =
        Streams.concat(schedule.streamMilestoneBlocks(), Stream.of(Long.MAX_VALUE))
            .map(
                block -> {
                  blockNumber.set(block);
                  return forkIdManager.getForkIdForChainHead();
                })
            .collect(Collectors.toList());

    assertThat(actualForkIds).containsExactlyElementsOf(expectedForkIds);
  }

  private static MilestoneStreamingTransitionProtocolSchedule createSchedule(
      final GenesisConfig genesisConfig) {
    final GenesisConfigOptions configOptions = genesisConfig.getConfigOptions();
    MilestoneStreamingProtocolSchedule preMergeProtocolSchedule =
        new MilestoneStreamingProtocolSchedule(
            (DefaultProtocolSchedule)
                MainnetProtocolSchedule.fromConfig(
                    configOptions,
                    MiningConfiguration.MINING_DISABLED,
                    new BadBlockManager(),
                    false,
                    new NoOpMetricsSystem()));
    MilestoneStreamingProtocolSchedule postMergeProtocolSchedule =
        new MilestoneStreamingProtocolSchedule(
            (DefaultProtocolSchedule)
                MergeProtocolSchedule.create(
                    configOptions,
                    false,
                    MiningConfiguration.MINING_DISABLED,
                    new BadBlockManager(),
                    false,
                    new NoOpMetricsSystem()));
    final MilestoneStreamingTransitionProtocolSchedule schedule =
        new MilestoneStreamingTransitionProtocolSchedule(
            preMergeProtocolSchedule, postMergeProtocolSchedule);
    return schedule;
  }

  public static class MilestoneStreamingTransitionProtocolSchedule
      extends TransitionProtocolSchedule {

    private final TransitionUtils<MilestoneStreamingProtocolSchedule> transitionUtils;

    public MilestoneStreamingTransitionProtocolSchedule(
        final MilestoneStreamingProtocolSchedule preMergeProtocolSchedule,
        final MilestoneStreamingProtocolSchedule postMergeProtocolSchedule) {
      super(preMergeProtocolSchedule, postMergeProtocolSchedule, PostMergeContext.get());
      transitionUtils =
          new TransitionUtils<>(
              preMergeProtocolSchedule, postMergeProtocolSchedule, PostMergeContext.get());
    }

    public Stream<Long> streamMilestoneBlocks() {
      return transitionUtils.dispatchFunctionAccordingToMergeState(
          MilestoneStreamingProtocolSchedule::streamMilestoneBlocks);
    }
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
