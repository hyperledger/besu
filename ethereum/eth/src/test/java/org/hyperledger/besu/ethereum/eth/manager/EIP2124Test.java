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
package org.hyperledger.besu.ethereum.eth.manager;

import static com.google.common.primitives.Longs.asList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EIP2124Test {
  private static final Logger LOG = LogManager.getLogger();

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // Mainnet test cases
          {
            "Mainnet // Unsynced",
            Network.MAINNET,
            0L,
            wantForkId("0xfc64ec04", 1150000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Homestead block",
            Network.MAINNET,
            1150000L,
            wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Homestead block",
            Network.MAINNET,
            1919999L,
            wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First DAO block",
            Network.MAINNET,
            1920000L,
            wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last DAO block",
            Network.MAINNET,
            2462999L,
            wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Tangerine block",
            Network.MAINNET,
            2463000L,
            wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Tangerine block",
            Network.MAINNET,
            2674999L,
            wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Spurious block",
            Network.MAINNET,
            2675000L,
            wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Spurious block",
            Network.MAINNET,
            4369999L,
            wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Byzantium block",
            Network.MAINNET,
            4370000L,
            wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Byzantium block",
            Network.MAINNET,
            7279999L,
            wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First and last Constantinople, first Petersburg block",
            Network.MAINNET,
            7280000L,
            wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Petersburg block",
            Network.MAINNET,
            9068999L,
            wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Istanbul and first Muir Glacier block",
            Network.MAINNET,
            9069000L,
            wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Istanbul and first Muir Glacier block",
            Network.MAINNET,
            9199999L,
            wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Muir Glacier block",
            Network.MAINNET,
            9200000L,
            wantForkId("0xe029e991", 0L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Future Muir Glacier block",
            Network.MAINNET,
            10000000L,
            wantForkId("0xe029e991", 0L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          // Ropsten test cases
          {
            "Ropsten // Unsynced, last Frontier, Homestead and first Tangerine block",
            Network.ROPSTEN,
            0L,
            wantForkId("0x30c7ddbc", 10L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Tangerine block",
            Network.ROPSTEN,
            9L,
            wantForkId("0x30c7ddbc", 10L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Spurious block",
            Network.ROPSTEN,
            10L,
            wantForkId("0x63760190", 1700000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Spurious block",
            Network.ROPSTEN,
            1699999L,
            wantForkId("0x63760190", 1700000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Byzantium block",
            Network.ROPSTEN,
            1700000L,
            wantForkId("0x3ea159c7", 4230000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Byzantium block",
            Network.ROPSTEN,
            4229999L,
            wantForkId("0x3ea159c7", 4230000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Constantinople block",
            Network.ROPSTEN,
            4230000L,
            wantForkId("0x97b544f3", 4939394L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Constantinople block",
            Network.ROPSTEN,
            4939393L,
            wantForkId("0x97b544f3", 4939394L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Petersburg block",
            Network.ROPSTEN,
            4939394L,
            wantForkId("0xd6e2149b", 6485846L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Petersburg block",
            Network.ROPSTEN,
            6485845L,
            wantForkId("0xd6e2149b", 6485846L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Istanbul block",
            Network.ROPSTEN,
            6485846L,
            wantForkId("0x4bc66396", 7117117L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Istanbul block",
            Network.ROPSTEN,
            7117116L,
            wantForkId("0x4bc66396", 7117117L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Muir Glacier block",
            Network.ROPSTEN,
            7117117L,
            wantForkId("0x6727ef90", 0L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Future",
            Network.ROPSTEN,
            7500000L,
            wantForkId("0x6727ef90", 0L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          // Rinkeby test cases
          {
            "Rinkeby // Unsynced, last Frontier block",
            Network.RINKEBY,
            0L,
            wantForkId("0x3b8e0691", 1L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First and last Homestead block",
            Network.RINKEBY,
            1L,
            wantForkId("0x60949295", 2L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First and last Tangerine block",
            Network.RINKEBY,
            2L,
            wantForkId("0x8bde40dd", 3L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Spurious block",
            Network.RINKEBY,
            3L,
            wantForkId("0xcb3a64bb", 1035301L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Spurious block",
            Network.RINKEBY,
            1035300L,
            wantForkId("0xcb3a64bb", 1035301L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Byzantium block",
            Network.RINKEBY,
            1035301L,
            wantForkId("0x8d748b57", 3660663L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Byzantium block",
            Network.RINKEBY,
            3660662L,
            wantForkId("0x8d748b57", 3660663L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Constantinople block",
            Network.RINKEBY,
            3660663L,
            wantForkId("0xe49cab14", 4321234L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Constantinople block",
            Network.RINKEBY,
            4321233L,
            wantForkId("0xe49cab14", 4321234L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Petersburg block",
            Network.RINKEBY,
            4321234L,
            wantForkId("0xafec6b27", 5435345L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Petersburg block",
            Network.RINKEBY,
            5435344L,
            wantForkId("0xafec6b27", 5435345L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Istanbul block",
            Network.RINKEBY,
            5435345L,
            wantForkId("0xcbdb8838", 0L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Future Istanbul block",
            Network.RINKEBY,
            6000000L,
            wantForkId("0xcbdb8838", 0L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          // Goerli test cases
          {
            "Goerli  // Unsynced, last Frontier, Homestead, Tangerine, Spurious, Byzantium, Constantinople and first Petersburg block",
            Network.GOERLI,
            0L,
            wantForkId("0xa3f5ab08", 1561651L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // Last Petersburg block",
            Network.GOERLI,
            1561650L,
            wantForkId("0xa3f5ab08", 1561651L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // First Istanbul block",
            Network.GOERLI,
            1561651L,
            wantForkId("0xc25efa5c", 0L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // Future Istanbul block",
            Network.GOERLI,
            2000000L,
            wantForkId("0xc25efa5c", 0L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          // Private network test cases
          {
            "Private // Unsynced",
            Network.PRIVATE,
            0L,
            wantForkId("0x190a55ad", 0L),
            empty(),
            empty()
          },
          {
            "Private // First block",
            Network.PRIVATE,
            1L,
            wantForkId("0x190a55ad", 0L),
            empty(),
            empty()
          },
          {
            "Private // Future block",
            Network.PRIVATE,
            1000000L,
            wantForkId("0x190a55ad", 0L),
            empty(),
            empty()
          },
          // Peer check cases
          {
            "check1PetersburgWithRemoteAnnouncingTheSame",
            Network.MAINNET,
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 0L, true)
          },
          {
            "check2PetersburgWithRemoteAnnouncingTheSameAndNextFork",
            Network.MAINNET,
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", Long.MAX_VALUE, true)
          },
          {
            "check3ByzantiumAwareOfPetersburgRemoteUnawareOfPetersburg",
            Network.MAINNET,
            7279999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)
          },
          {
            "check4ByzantiumAwareOfPetersburgRemoteAwareOfPetersburg",
            Network.MAINNET,
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7280000L, true)
          },
          {
            "check5ByzantiumAwareOfPetersburgRemoteAnnouncingUnknownFork",
            Network.MAINNET,
            7279999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", Long.MAX_VALUE, true)
          },
          {
            "check6PetersburgWithRemoteAnnouncingByzantiumAwareOfPetersburg",
            Network.MAINNET,
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 7280000L, true)
          },
          {
            "check7PetersburgWithRemoteAnnouncingSpuriousAwareOfByzantiumRemoteMayNeedUpdate",
            Network.MAINNET,
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0x3edd5b10", 4370000L, true)
          },
          {
            "check8ByzantiumWithRemoteAnnouncingPetersburgLocalOutOfSync",
            Network.MAINNET,
            727999L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 0L, true)
          },
          {
            "check9SpuriousWithRemoteAnnouncingByzantiumRemoteUnawareOfPetersburg",
            Network.MAINNET,
            4369999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)
          },
          {
            "check10PetersburgWithRemoteAnnouncingByzantiumRemoteUnawareOfAdditionalForks",
            Network.network(
                GenesisHash.MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L)),
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, false)
          },
          {
            "check11PetersburgWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate",
            Network.network(
                GenesisHash.MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L)),
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0x5cddc0e1", 0L, false)
          },
          {
            "check12ByzantiumWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate",
            Network.network(
                GenesisHash.MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L)),
            7279999L,
            empty(),
            empty(),
            wantPeerCheck("0x5cddc0e1", 0L, false)
          },
          {
            "check13ByzantiumWithRemoteAnnouncingRinkebyPetersburg",
            Network.network(
                GenesisHash.MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L)),
            7987396L,
            empty(),
            empty(),
            wantPeerCheck("0xafec6b27", 0L, false)
          }
        });
  }

  private final String name;
  private final Network network;
  private final long head;
  private final Optional<ForkId> wantForkId;
  private final Optional<List<ForkId>> wantForkIds;
  private final Optional<PeerCheckCase> wantPeerCheckCase;

  @Test
  public void test() {
    LOG.info("Running test case {}", name);
    final ForkIdManager forkIdManager =
        new ForkIdManager(mockBlockchain(network.hash, head), network.forks);
    wantForkId.ifPresent(forkId -> assertThat(forkIdManager.computeForkId()).isEqualTo(forkId));
    wantForkIds.ifPresent(
        forkIds ->
            assertThat(forkIdManager.getForkAndHashList()).containsExactlyElementsOf(forkIds));
    wantPeerCheckCase.ifPresent(
        peerCheckCase ->
            assertThat(
                    forkIdManager.peerCheck(
                        new ForkId(
                            Bytes.fromHexString(peerCheckCase.forkIdHash),
                            peerCheckCase.forkIdNext)))
                .isEqualTo(peerCheckCase.want));
  }

  public EIP2124Test(
      final String name,
      final Network network,
      final long head,
      final Optional<ForkId> wantForkId,
      final Optional<List<ForkId>> wantForkIds,
      final Optional<PeerCheckCase> wantPeerCheckCase) {
    this.name = name;
    this.network = network;
    this.head = head;
    this.wantForkId = wantForkId;
    this.wantForkIds = wantForkIds;
    this.wantPeerCheckCase = wantPeerCheckCase;
  }

  private static Blockchain mockBlockchain(final String genesisHash, final long chainHeight) {
    final Blockchain mockchain = mock(Blockchain.class);
    final BlockHeader mockHeader = mock(BlockHeader.class);
    final Block block = new Block(mockHeader, null);
    when(mockchain.getGenesisBlock()).thenReturn(block);
    when(mockchain.getChainHeadBlockNumber()).thenReturn(chainHeight);
    when(mockHeader.getHash()).thenReturn(Hash.fromHexString(genesisHash));
    return mockchain;
  }

  private static class GenesisHash {
    private static final String MAINNET =
        "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
    private static final String ROPSTEN =
        "0x41941023680923e0fe4d74a34bdac8141f2540e3ae90623718e47d66d1ca4a2d";
    private static final String RINKEBY =
        "0x6341fd3daf94b748c72ced5a5b26028f2474f5f00d824504e4fa37a75767e177";
    private static final String GOERLI =
        "0xbf7e331f7f7c1dd2e05159666b3bf8bc7a8a3a9eb1d518969eab529dd9b88c1a";
    private static final String PRIVATE =
        "0x0000000000000000000000000000000000000000000000000000000000000000";
  }

  private static class Forks {
    private static final List<Long> MAINNET =
        Arrays.asList(
            1920000L, 1150000L, 2463000L, 2675000L, 2675000L, 4370000L, 7280000L, 7280000L,
            9069000L, 9200000L);
    private static final List<Long> ROPSTEN =
        Arrays.asList(0L, 0L, 10L, 1700000L, 4230000L, 4939394L, 6485846L, 7117117L);
    private static final List<Long> RINKEBY =
        Arrays.asList(1L, 2L, 3L, 3L, 1035301L, 3660663L, 4321234L, 5435345L);
    private static final List<Long> GOERLI = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1561651L);
    private static final List<Long> PRIVATE = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }

  private static class ForkIds {
    private static final List<ForkId> MAINNET =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0xfc64ec04"), 1150000L), // Unsynced
            new ForkId(Bytes.fromHexString("0x97c2c34c"), 1920000L), // First Homestead block
            new ForkId(Bytes.fromHexString("0x91d1f948"), 2463000L), // First DAO block
            new ForkId(Bytes.fromHexString("0x7a64da13"), 2675000L), // First Tangerine block
            new ForkId(Bytes.fromHexString("0x3edd5b10"), 4370000L), // First Spurious block
            new ForkId(Bytes.fromHexString("0xa00bc324"), 7280000L), // First Byzantium block
            new ForkId(Bytes.fromHexString("0x668db0af"), 9069000L),
            new ForkId(Bytes.fromHexString("0x879d6e30"), 9200000L),
            new ForkId(Bytes.fromHexString("0xe029e991"), 0L));
    private static final List<ForkId> ROPSTEN =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0x30c7ddbc"), 10L),
            new ForkId(Bytes.fromHexString("0x63760190"), 1700000L),
            new ForkId(Bytes.fromHexString("0x3ea159c7"), 4230000L),
            new ForkId(Bytes.fromHexString("0x97b544f3"), 4939394L),
            new ForkId(Bytes.fromHexString("0xd6e2149b"), 6485846L),
            new ForkId(Bytes.fromHexString("0x4bc66396"), 7117117L),
            new ForkId(Bytes.fromHexString("0x6727ef90"), 0L));
    private static final List<ForkId> RINKEBY =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0x3b8e0691"), 1L),
            new ForkId(Bytes.fromHexString("0x60949295"), 2L),
            new ForkId(Bytes.fromHexString("0x8bde40dd"), 3L),
            new ForkId(Bytes.fromHexString("0xcb3a64bb"), 1035301L),
            new ForkId(Bytes.fromHexString("0x8d748b57"), 3660663L),
            new ForkId(Bytes.fromHexString("0xe49cab14"), 4321234L),
            new ForkId(Bytes.fromHexString("0xafec6b27"), 5435345L),
            new ForkId(Bytes.fromHexString("0xcbdb8838"), 0L));
    private static final List<ForkId> GOERLI =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0xa3f5ab08"), 1561651L),
            new ForkId(Bytes.fromHexString("0xc25efa5c"), 0L));
  }

  private static class Network {
    private static final Network MAINNET = network(GenesisHash.MAINNET, Forks.MAINNET);
    private static final Network ROPSTEN = network(GenesisHash.ROPSTEN, Forks.ROPSTEN);
    private static final Network RINKEBY = network(GenesisHash.RINKEBY, Forks.RINKEBY);
    private static final Network GOERLI = network(GenesisHash.GOERLI, Forks.GOERLI);
    private static final Network PRIVATE = network(GenesisHash.PRIVATE, Forks.PRIVATE);
    private final String hash;
    private final List<Long> forks;

    Network(final String hash, final List<Long> forks) {
      this.hash = hash;
      this.forks = forks;
    }

    private static Network network(final String hash, final List<Long> forks) {
      return new Network(hash, forks);
    }
  }

  private static class PeerCheckCase {
    private final String forkIdHash;
    private final long forkIdNext;
    private final boolean want;

    private PeerCheckCase(final String forkIdHash, final long forkIdNext, final boolean want) {
      this.forkIdHash = forkIdHash;
      this.forkIdNext = forkIdNext;
      this.want = want;
    }
  }

  private static ForkId forkId(final String hash, final long next) {
    return new ForkId(Bytes.fromHexString(hash), next);
  }

  private static Optional<ForkId> wantForkId(final String hash, final long next) {
    return Optional.of(forkId(hash, next));
  }

  private static Optional<PeerCheckCase> wantPeerCheck(
      final String hash, final long next, final boolean want) {
    return Optional.of(new PeerCheckCase(hash, next, want));
  }
}
