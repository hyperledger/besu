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
import static org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.mockBlockchain;
import static org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.wantPeerCheck;

import org.hyperledger.besu.ethereum.eth.ForkIdTestUtil;
import org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.ForkIds;
import org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.GenesisHash;
import org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.Network;
import org.hyperledger.besu.ethereum.eth.ForkIdTestUtil.PeerCheckCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class EIP2124Test {
  private static final Logger LOG = LoggerFactory.getLogger(EIP2124Test.class);

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // Mainnet test cases
          {
            "Mainnet // Unsynced",
            Network.MAINNET,
            0L,
            ForkIdTestUtil.wantForkId("0xfc64ec04", 1150000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Homestead block",
            Network.MAINNET,
            1150000L,
            ForkIdTestUtil.wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Homestead block",
            Network.MAINNET,
            1919999L,
            ForkIdTestUtil.wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First DAO block",
            Network.MAINNET,
            1920000L,
            ForkIdTestUtil.wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last DAO block",
            Network.MAINNET,
            2462999L,
            ForkIdTestUtil.wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Tangerine block",
            Network.MAINNET,
            2463000L,
            ForkIdTestUtil.wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Tangerine block",
            Network.MAINNET,
            2674999L,
            ForkIdTestUtil.wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Spurious block",
            Network.MAINNET,
            2675000L,
            ForkIdTestUtil.wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Spurious block",
            Network.MAINNET,
            4369999L,
            ForkIdTestUtil.wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Byzantium block",
            Network.MAINNET,
            4370000L,
            ForkIdTestUtil.wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Byzantium block",
            Network.MAINNET,
            7279999L,
            ForkIdTestUtil.wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First and last Constantinople, first Petersburg block",
            Network.MAINNET,
            7280000L,
            ForkIdTestUtil.wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Petersburg block",
            Network.MAINNET,
            9068999L,
            ForkIdTestUtil.wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Istanbul block",
            Network.MAINNET,
            9069000L,
            ForkIdTestUtil.wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Istanbul block",
            Network.MAINNET,
            9199999L,
            ForkIdTestUtil.wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Muir Glacier block",
            Network.MAINNET,
            9200000L,
            ForkIdTestUtil.wantForkId("0xe029e991", 12244000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Muir Glacier block",
            Network.MAINNET,
            12243999L,
            ForkIdTestUtil.wantForkId("0xe029e991", 12244000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Berlin block",
            Network.MAINNET,
            12244000L,
            ForkIdTestUtil.wantForkId("0x0eb440f6", 12965000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last Berlin block",
            Network.MAINNET,
            12964999L,
            ForkIdTestUtil.wantForkId("0x0eb440f6", 12965000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First London block",
            Network.MAINNET,
            12965000L,
            ForkIdTestUtil.wantForkId("0xb715077d", 13773000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Last London block",
            Network.MAINNET,
            13772999L,
            ForkIdTestUtil.wantForkId("0xb715077d", 13773000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Arrow Glacier block",
            Network.MAINNET,
            13773000L,
            ForkIdTestUtil.wantForkId("0x20c327fc", 15050000L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // First Gray Glacier block",
            Network.MAINNET,
            15050000L,
            ForkIdTestUtil.wantForkId("0xf0afd0e3", 0L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          {
            "Mainnet // Future Gray Glacier block",
            Network.MAINNET,
            20000000L,
            ForkIdTestUtil.wantForkId("0xf0afd0e3", 0L),
            Optional.of(ForkIds.MAINNET),
            empty()
          },
          // Ropsten test cases
          {
            "Ropsten // Unsynced, last Frontier, Homestead and first Tangerine block",
            Network.ROPSTEN,
            0L,
            ForkIdTestUtil.wantForkId("0x30c7ddbc", 10L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Tangerine block",
            Network.ROPSTEN,
            9L,
            ForkIdTestUtil.wantForkId("0x30c7ddbc", 10L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Spurious block",
            Network.ROPSTEN,
            10L,
            ForkIdTestUtil.wantForkId("0x63760190", 1700000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Spurious block",
            Network.ROPSTEN,
            1699999L,
            ForkIdTestUtil.wantForkId("0x63760190", 1700000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Byzantium block",
            Network.ROPSTEN,
            1700000L,
            ForkIdTestUtil.wantForkId("0x3ea159c7", 4230000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Byzantium block",
            Network.ROPSTEN,
            4229999L,
            ForkIdTestUtil.wantForkId("0x3ea159c7", 4230000L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Constantinople block",
            Network.ROPSTEN,
            4230000L,
            ForkIdTestUtil.wantForkId("0x97b544f3", 4939394L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Constantinople block",
            Network.ROPSTEN,
            4939393L,
            ForkIdTestUtil.wantForkId("0x97b544f3", 4939394L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Petersburg block",
            Network.ROPSTEN,
            4939394L,
            ForkIdTestUtil.wantForkId("0xd6e2149b", 6485846L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Petersburg block",
            Network.ROPSTEN,
            6485845L,
            ForkIdTestUtil.wantForkId("0xd6e2149b", 6485846L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Istanbul block",
            Network.ROPSTEN,
            6485846L,
            ForkIdTestUtil.wantForkId("0x4bc66396", 7117117L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Last Istanbul block",
            Network.ROPSTEN,
            7117116L,
            ForkIdTestUtil.wantForkId("0x4bc66396", 7117117L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // First Muir Glacier block",
            Network.ROPSTEN,
            7117117L,
            ForkIdTestUtil.wantForkId("0x6727ef90", 0L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          {
            "Ropsten // Future",
            Network.ROPSTEN,
            7500000L,
            ForkIdTestUtil.wantForkId("0x6727ef90", 0L),
            Optional.of(ForkIds.ROPSTEN),
            empty()
          },
          // Sepolia test cases
          {
            "Sepolia // mergenetsplit block",
            Network.SEPOLIA,
            0L,
            ForkIdTestUtil.wantForkId("0xfe3366e7", 1735371L),
            Optional.of(ForkIds.SEPOLIA),
            empty()
          },
          {
            "Sepolia // Future",
            Network.SEPOLIA,
            1735371L,
            ForkIdTestUtil.wantForkId("0xb96cbd13", 0L),
            Optional.of(ForkIds.SEPOLIA),
            empty()
          },
          // Rinkeby test cases
          {
            "Rinkeby // Unsynced, last Frontier block",
            Network.RINKEBY,
            0L,
            ForkIdTestUtil.wantForkId("0x3b8e0691", 1L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First and last Homestead block",
            Network.RINKEBY,
            1L,
            ForkIdTestUtil.wantForkId("0x60949295", 2L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First and last Tangerine block",
            Network.RINKEBY,
            2L,
            ForkIdTestUtil.wantForkId("0x8bde40dd", 3L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Spurious block",
            Network.RINKEBY,
            3L,
            ForkIdTestUtil.wantForkId("0xcb3a64bb", 1035301L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Spurious block",
            Network.RINKEBY,
            1035300L,
            ForkIdTestUtil.wantForkId("0xcb3a64bb", 1035301L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Byzantium block",
            Network.RINKEBY,
            1035301L,
            ForkIdTestUtil.wantForkId("0x8d748b57", 3660663L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Byzantium block",
            Network.RINKEBY,
            3660662L,
            ForkIdTestUtil.wantForkId("0x8d748b57", 3660663L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Constantinople block",
            Network.RINKEBY,
            3660663L,
            ForkIdTestUtil.wantForkId("0xe49cab14", 4321234L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Constantinople block",
            Network.RINKEBY,
            4321233L,
            ForkIdTestUtil.wantForkId("0xe49cab14", 4321234L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Petersburg block",
            Network.RINKEBY,
            4321234L,
            ForkIdTestUtil.wantForkId("0xafec6b27", 5435345L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Last Petersburg block",
            Network.RINKEBY,
            5435344L,
            ForkIdTestUtil.wantForkId("0xafec6b27", 5435345L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // First Istanbul block",
            Network.RINKEBY,
            5435345L,
            ForkIdTestUtil.wantForkId("0xcbdb8838", 0L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          {
            "Rinkeby // Future Istanbul block",
            Network.RINKEBY,
            6000000L,
            ForkIdTestUtil.wantForkId("0xcbdb8838", 0L),
            Optional.of(ForkIds.RINKEBY),
            empty()
          },
          // Goerli test cases
          {
            "Goerli  // Unsynced, last Frontier, Homestead, Tangerine, Spurious, Byzantium, Constantinople and first Petersburg block",
            Network.GOERLI,
            0L,
            ForkIdTestUtil.wantForkId("0xa3f5ab08", 1561651L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // Last Petersburg block",
            Network.GOERLI,
            1561650L,
            ForkIdTestUtil.wantForkId("0xa3f5ab08", 1561651L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // First Istanbul block",
            Network.GOERLI,
            1561651L,
            ForkIdTestUtil.wantForkId("0xc25efa5c", 0L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          {
            "Goerli // Future Istanbul block",
            Network.GOERLI,
            2000000L,
            ForkIdTestUtil.wantForkId("0xc25efa5c", 0L),
            Optional.of(ForkIds.GOERLI),
            empty()
          },
          // Private network test cases
          {
            "Private // Unsynced",
            Network.PRIVATE,
            0L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
            empty(),
            empty()
          },
          {
            "Private // First block",
            Network.PRIVATE,
            1L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
            empty(),
            empty()
          },
          {
            "Private // Future block",
            Network.PRIVATE,
            1000000L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
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
            ForkIdTestUtil.Network.network(
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
        new ForkIdManager(mockBlockchain(network.hash, head), network.forks, false);
    wantForkId.ifPresent(
        forkId -> assertThat(forkIdManager.getForkIdForChainHead()).isEqualTo(forkId));
    wantForkIds.ifPresent(
        forkIds -> assertThat(forkIdManager.getForkIds()).containsExactlyElementsOf(forkIds));
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
      final ForkIdTestUtil.Network network,
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
}
