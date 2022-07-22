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
 *
 */

package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.eth.manager.ForkId;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForkIdsTest {

  @Parameterized.Parameter public NetworkName chainName;

  @Parameterized.Parameter(1)
  public List<ForkId> expectedForkIds;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[] {
          NetworkName.SEPOLIA,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xfe3366e7L), 1735371L),
              new ForkId(Bytes.ofUnsignedInt(0xb96cbd13L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xb96cbd13L), 0L))
        },
        new Object[] {
          NetworkName.ROPSTEN,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0x30c7ddbcL), 10L),
              new ForkId(Bytes.ofUnsignedInt(0x63760190L), 1700000L),
              new ForkId(Bytes.ofUnsignedInt(0x3ea159c7L), 4230000L),
              new ForkId(Bytes.ofUnsignedInt(0x97b544f3L), 4939394L),
              new ForkId(Bytes.ofUnsignedInt(0xd6e2149bL), 6485846L),
              new ForkId(Bytes.ofUnsignedInt(0x4bc66396L), 7117117L),
              new ForkId(Bytes.ofUnsignedInt(0x6727ef90L), 9812189L),
              new ForkId(Bytes.ofUnsignedInt(0xa157d377L), 10499401L),
              new ForkId(Bytes.ofUnsignedInt(0x7119b6b3L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x7119b6b3L), 0L))
        },
        new Object[] {
          NetworkName.RINKEBY,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0x3b8e0691L), 1L),
              new ForkId(Bytes.ofUnsignedInt(0x60949295L), 2L),
              new ForkId(Bytes.ofUnsignedInt(0x8bde40ddL), 3L),
              new ForkId(Bytes.ofUnsignedInt(0xcb3a64bbL), 1035301L),
              new ForkId(Bytes.ofUnsignedInt(0x8d748b57L), 3660663L),
              new ForkId(Bytes.ofUnsignedInt(0xe49cab14L), 4321234L),
              new ForkId(Bytes.ofUnsignedInt(0xafec6b27L), 5435345L),
              new ForkId(Bytes.ofUnsignedInt(0xcbdb8838L), 8290928L),
              new ForkId(Bytes.ofUnsignedInt(0x6910c8bdL), 8897988L),
              new ForkId(Bytes.ofUnsignedInt(0x8e29f2f3L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x8e29f2f3L), 0L))
        },
        new Object[] {
          NetworkName.GOERLI,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0xa3f5ab08L), 1561651L),
              new ForkId(Bytes.ofUnsignedInt(0xc25efa5cL), 4460644L),
              new ForkId(Bytes.ofUnsignedInt(0x757a1c47L), 5062605L),
              new ForkId(Bytes.ofUnsignedInt(0xb8c6299dL), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xb8c6299dL), 0L))
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
              new ForkId(Bytes.ofUnsignedInt(0xf0afd0e3L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xf0afd0e3L), 0L))
        },
        new Object[] {
          NetworkName.MORDOR,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0x175782aaL), 301243L),
              new ForkId(Bytes.ofUnsignedInt(0x604f6ee1L), 999983L),
              new ForkId(Bytes.ofUnsignedInt(0xf42f5539L), 2520000L),
              new ForkId(Bytes.ofUnsignedInt(0x66b5c286L), 3985893),
              new ForkId(Bytes.ofUnsignedInt(0x92b323e0L), 5520000L),
              new ForkId(Bytes.ofUnsignedInt(0x8c9b1797L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x8c9b1797L), 0L))
        },
        new Object[] {
          NetworkName.KOTTI,
          List.of(
              new ForkId(Bytes.ofUnsignedInt(0x550152eL), 716617L),
              new ForkId(Bytes.ofUnsignedInt(0xa3270822L), 1705549L),
              new ForkId(Bytes.ofUnsignedInt(0x8f3698e0L), 2200013L),
              new ForkId(Bytes.ofUnsignedInt(0x6f402821L), 4368634),
              new ForkId(Bytes.ofUnsignedInt(0xf03e54e7L), 5578000L),
              new ForkId(Bytes.ofUnsignedInt(0xc5459816L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0xc5459816L), 0L))
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
              new ForkId(Bytes.ofUnsignedInt(0x7fd1bb25L), 0L),
              new ForkId(Bytes.ofUnsignedInt(0x7fd1bb25L), 0L))
        });
  }

  @Test
  public void testForkId() {
    final GenesisConfigFile genesisConfigFile =
        GenesisConfigFile.fromConfig(EthNetworkConfig.jsonConfig(chainName));
    final GenesisConfigOptions configOptions = genesisConfigFile.getConfigOptions();
    final ProtocolSchedule schedule =
        MainnetProtocolSchedule.fromConfig(configOptions, EvmConfiguration.DEFAULT);
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfigFile, schedule);
    final Blockchain mockBlockchain = mock(Blockchain.class);

    when(mockBlockchain.getGenesisBlock()).thenReturn(genesisState.getBlock());

    final AtomicLong blockNumber = new AtomicLong();
    when(mockBlockchain.getChainHeadBlockNumber()).thenAnswer(o -> blockNumber.get());

    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain, genesisConfigFile.getForks(), false);

    final var actualForkIds =
        Streams.concat(
                ((MutableProtocolSchedule) schedule).streamMilestoneBlocks(),
                Stream.of(Long.MAX_VALUE))
            .map(
                block -> {
                  blockNumber.set(block);
                  return forkIdManager.getForkIdForChainHead();
                })
            .collect(Collectors.toList());

    assertThat(actualForkIds).containsExactlyElementsOf(expectedForkIds);
  }
}
