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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager.ForkId;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
          {"Mainnet // First Homestead block", Network.MAINNET, 1150000L, "0x97c2c34c", 1920000L},
          {"Mainnet // Last Homestead block", Network.MAINNET, 1919999L, "0x97c2c34c", 1920000L},
          {"Mainnet // First DAO block", Network.MAINNET, 1920000L, "0x91d1f948", 2463000L},
          {"Mainnet // Last DAO block", Network.MAINNET, 2462999L, "0x91d1f948", 2463000L},
          {"Mainnet // First Tangerine block", Network.MAINNET, 2463000L, "0x7a64da13", 2675000L},
          {"Mainnet // Last Tangerine block", Network.MAINNET, 2674999L, "0x7a64da13", 2675000L},
          {"Mainnet // First Spurious block", Network.MAINNET, 2675000L, "0x3edd5b10", 4370000L},
          {"Mainnet // Last Spurious block", Network.MAINNET, 4369999L, "0x3edd5b10", 4370000L},
          {"Mainnet // First Byzantium block", Network.MAINNET, 4370000L, "0xa00bc324", 7280000L},
          {"Mainnet // Last Byzantium block", Network.MAINNET, 7279999L, "0xa00bc324", 7280000L},
          {
            "Mainnet // First and last Constantinople, first Petersburg block",
            Network.MAINNET,
            7280000L,
            "0x668db0af",
            9069000L
          },
          {"Mainnet // Last Petersburg block", Network.MAINNET, 9068999L, "0x668db0af", 9069000L},
          {
            "Mainnet // First Istanbul and first Muir Glacier block",
            Network.MAINNET,
            9069000L,
            "0x879d6e30",
            9200000L
          },
          {
            "Mainnet // Last Istanbul and first Muir Glacier block",
            Network.MAINNET,
            9199999L,
            "0x879d6e30",
            9200000L
          },
          {"Mainnet // First Muir Glacier block", Network.MAINNET, 9200000L, "0xe029e991", 0L},
          {"Mainnet // Future Muir Glacier block", Network.MAINNET, 10000000L, "0xe029e991", 0L},
          // Ropsten test cases
          {
            "Ropsten // Unsynced, last Frontier, Homestead and first Tangerine block",
            Network.ROPSTEN,
            0L,
            "0x30c7ddbc",
            10L
          },
          {"Ropsten // Last Tangerine block", Network.ROPSTEN, 9L, "0x30c7ddbc", 10L},
          {"Ropsten // First Spurious block", Network.ROPSTEN, 10L, "0x63760190", 1700000L},
          {"Ropsten // Last Spurious block", Network.ROPSTEN, 1699999L, "0x63760190", 1700000L},
          {"Ropsten // First Byzantium block", Network.ROPSTEN, 1700000L, "0x3ea159c7", 4230000L},
          {"Ropsten // First Byzantium block", Network.ROPSTEN, 4229999L, "0x3ea159c7", 4230000L},
          {
            "Ropsten // First Constantinople block",
            Network.ROPSTEN,
            4230000L,
            "0x97b544f3",
            4939394L
          },
          {
            "Ropsten // Last Constantinople block",
            Network.ROPSTEN,
            4939393L,
            "0x97b544f3",
            4939394L
          },
          {"Ropsten // First Petersburg block", Network.ROPSTEN, 4939394L, "0xd6e2149b", 6485846L},
          {"Ropsten // Last Petersburg block", Network.ROPSTEN, 6485845L, "0xd6e2149b", 6485846L},
          {"Ropsten // First Istanbul block", Network.ROPSTEN, 6485846L, "0x4bc66396", 7117117L},
          {"Ropsten // Last Istanbul block", Network.ROPSTEN, 7117116L, "0x4bc66396", 7117117L},
          {"Ropsten // First Muir Glacier block", Network.ROPSTEN, 7117117L, "0x6727ef90", 0L},
          {"Ropsten // Future", Network.ROPSTEN, 7500000L, "0x6727ef90", 0L},
          // Rinkeby test cases
          {"Rinkeby // Unsynced, last Frontier block", Network.RINKEBY, 0L, "0x3b8e0691", 1L},
          {"Rinkeby // First and last Homestead block", Network.RINKEBY, 1L, "0x60949295", 2L},
          {"Rinkeby // First and last Tangerine block", Network.RINKEBY, 2L, "0x8bde40dd", 3L},
          {"Rinkeby // First Spurious block", Network.RINKEBY, 3L, "0xcb3a64bb", 1035301L},
          {"Rinkeby // Last Spurious block", Network.RINKEBY, 1035300L, "0xcb3a64bb", 1035301L},
          {"Rinkeby // First Byzantium block", Network.RINKEBY, 1035301L, "0x8d748b57", 3660663L},
          {"Rinkeby // Last Byzantium block", Network.RINKEBY, 3660662L, "0x8d748b57", 3660663L},
          {
            "Rinkeby // First Constantinople block",
            Network.RINKEBY,
            3660663L,
            "0xe49cab14",
            4321234L
          },
          {
            "Rinkeby // Last Constantinople block",
            Network.RINKEBY,
            4321233L,
            "0xe49cab14",
            4321234L
          },
          {"Rinkeby // First Petersburg block", Network.RINKEBY, 4321234L, "0xafec6b27", 5435345L},
          {"Rinkeby // Last Petersburg block", Network.RINKEBY, 5435344L, "0xafec6b27", 5435345L},
          {"Rinkeby // First Istanbul block", Network.RINKEBY, 5435345L, "0xcbdb8838", 0L},
          {"Rinkeby // Future Istanbul block", Network.RINKEBY, 6000000L, "0xcbdb8838", 0L},
          // Goerli test cases
          {
            "Goerli  // Unsynced, last Frontier, Homestead, Tangerine, Spurious, Byzantium, Constantinople and first Petersburg block",
            Network.GOERLI,
            0L,
            "0xa3f5ab08",
            1561651L
          },
          {"Goerli // Last Petersburg block", Network.GOERLI, 1561650L, "0xa3f5ab08", 1561651L},
          {"Goerli // First Istanbul block", Network.GOERLI, 1561651L, "0xc25efa5c", 0L},
          {"Goerli // Future Istanbul block", Network.GOERLI, 2000000L, "0xc25efa5c", 0L},
          // Private network test cases
          {"Private // Unsynced", Network.PRIVATE, 0L, "0x190a55ad", 0L},
          {"Private // First block", Network.PRIVATE, 1L, "0x190a55ad", 0L},
          {"Private // Future block", Network.PRIVATE, 1000000L, "0x190a55ad", 0L}
        });
  }

  private final String name;
  private final Network network;
  private final long head;
  private final String wantHash;
  private final long wantNext;

  @Test
  public void test() {
    LOG.info("Running test case {}", name);
    assertThat(new ForkIdManager(mockBlockchain(network.hash, head), network.forks).computeForkId())
        .isEqualTo(new ForkId(Bytes.fromHexString(wantHash), wantNext));
  }

  public EIP2124Test(
      final String name,
      final Network network,
      final long head,
      final String wantHash,
      final long wantNext) {
    this.name = name;
    this.network = network;
    this.head = head;
    this.wantHash = wantHash;
    this.wantNext = wantNext;
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

  private enum Network {
    MAINNET(GenesisHash.MAINNET, Forks.MAINNET),
    ROPSTEN(GenesisHash.ROPSTEN, Forks.ROPSTEN),
    RINKEBY(GenesisHash.RINKEBY, Forks.RINKEBY),
    GOERLI(GenesisHash.GOERLI, Forks.GOERLI),
    PRIVATE(GenesisHash.PRIVATE, Forks.PRIVATE);
    private final String hash;
    private final List<Long> forks;

    Network(final String hash, final List<Long> forks) {
      this.hash = hash;
      this.forks = forks;
    }
  }
}
