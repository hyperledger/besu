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
package org.hyperledger.besu.ethereum.eth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.ForkId;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.apache.tuweni.bytes.Bytes;

public class ForkIdTestUtil {

  public static Blockchain mockBlockchain(final String genesisHash, final long chainHeight) {
    return mockBlockchain(genesisHash, () -> chainHeight);
  }

  public static Blockchain mockBlockchain(
      final String genesisHash, final LongSupplier chainHeightSupplier) {
    final Blockchain mockchain = mock(Blockchain.class);
    final BlockHeader mockHeader = mock(BlockHeader.class);
    final Block block = new Block(mockHeader, null);
    when(mockchain.getGenesisBlock()).thenReturn(block);
    when(mockchain.getChainHeadBlockNumber()).thenReturn(chainHeightSupplier.getAsLong());
    when(mockHeader.getHash()).thenReturn(Hash.fromHexString(genesisHash));
    return mockchain;
  }

  public static class GenesisHash {
    public static final String MAINNET =
        "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
    public static final String ROPSTEN =
        "0x41941023680923e0fe4d74a34bdac8141f2540e3ae90623718e47d66d1ca4a2d";
    public static final String SEPOLIA =
        "0x25a5cc106eea7138acab33231d7160d69cb777ee0c2c553fcddf5138993e6dd9";
    public static final String RINKEBY =
        "0x6341fd3daf94b748c72ced5a5b26028f2474f5f00d824504e4fa37a75767e177";
    public static final String GOERLI =
        "0xbf7e331f7f7c1dd2e05159666b3bf8bc7a8a3a9eb1d518969eab529dd9b88c1a";
    public static final String PRIVATE =
        "0x0000000000000000000000000000000000000000000000000000000000000000";
  }

  public static class Forks {
    public static final List<Long> MAINNET =
        Arrays.asList(
            1920000L, 1150000L, 2463000L, 2675000L, 2675000L, 4370000L, 7280000L, 7280000L,
            9069000L, 9200000L, 12244000L, 12965000L, 13773000L, 15050000L);
    public static final List<Long> ROPSTEN =
        Arrays.asList(0L, 0L, 10L, 1700000L, 4230000L, 4939394L, 6485846L, 7117117L);
    public static final List<Long> SEPOLIA =
        Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1735371L);
    public static final List<Long> RINKEBY =
        Arrays.asList(1L, 2L, 3L, 3L, 1035301L, 3660663L, 4321234L, 5435345L);
    public static final List<Long> GOERLI = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1561651L);
    public static final List<Long> PRIVATE = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }

  public static class ForkIds {
    public static final List<ForkId> MAINNET =
        Arrays.asList(
            new ForkId(
                Bytes.fromHexString("0xfc64ec04"), 1150000L), // Unsynced / last Frontier block
            new ForkId(Bytes.fromHexString("0x97c2c34c"), 1920000L), // First Homestead block
            new ForkId(Bytes.fromHexString("0x91d1f948"), 2463000L), // First DAO block
            new ForkId(Bytes.fromHexString("0x7a64da13"), 2675000L), // First Tangerine block
            new ForkId(Bytes.fromHexString("0x3edd5b10"), 4370000L), // First Spurious block
            new ForkId(Bytes.fromHexString("0xa00bc324"), 7280000L), // First Byzantium block
            new ForkId(Bytes.fromHexString("0x668db0af"), 9069000L), // First Petersburg block
            new ForkId(Bytes.fromHexString("0x879d6e30"), 9200000L), // First Istanbul block
            new ForkId(Bytes.fromHexString("0xe029e991"), 12244000L), // First Muir Glacier block
            new ForkId(Bytes.fromHexString("0x0eb440f6"), 12965000L), // First Berlin block
            new ForkId(Bytes.fromHexString("0xb715077d"), 13773000L), // First London block
            new ForkId(Bytes.fromHexString("0x20c327fc"), 15050000L), // First Arrow Glacier block
            new ForkId(Bytes.fromHexString("0xf0afd0e3"), 0L)); // First Gray Glacier block
    public static final List<ForkId> ROPSTEN =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0x30c7ddbc"), 10L),
            new ForkId(Bytes.fromHexString("0x63760190"), 1700000L),
            new ForkId(Bytes.fromHexString("0x3ea159c7"), 4230000L),
            new ForkId(Bytes.fromHexString("0x97b544f3"), 4939394L),
            new ForkId(Bytes.fromHexString("0xd6e2149b"), 6485846L),
            new ForkId(Bytes.fromHexString("0x4bc66396"), 7117117L),
            new ForkId(Bytes.fromHexString("0x6727ef90"), 0L));
    public static final List<ForkId> SEPOLIA =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0xfe3366e7"), 1735371L),
            new ForkId(Bytes.fromHexString("0xb96cbd13"), 0L));
    public static final List<ForkId> RINKEBY =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0x3b8e0691"), 1L),
            new ForkId(Bytes.fromHexString("0x60949295"), 2L),
            new ForkId(Bytes.fromHexString("0x8bde40dd"), 3L),
            new ForkId(Bytes.fromHexString("0xcb3a64bb"), 1035301L),
            new ForkId(Bytes.fromHexString("0x8d748b57"), 3660663L),
            new ForkId(Bytes.fromHexString("0xe49cab14"), 4321234L),
            new ForkId(Bytes.fromHexString("0xafec6b27"), 5435345L),
            new ForkId(Bytes.fromHexString("0xcbdb8838"), 0L));
    public static final List<ForkId> GOERLI =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0xa3f5ab08"), 1561651L),
            new ForkId(Bytes.fromHexString("0xc25efa5c"), 0L));
  }

  public static class Network {
    public static final Network MAINNET = network(GenesisHash.MAINNET, Forks.MAINNET);
    public static final Network ROPSTEN = network(GenesisHash.ROPSTEN, Forks.ROPSTEN);
    public static final Network SEPOLIA = network(GenesisHash.SEPOLIA, Forks.SEPOLIA);
    public static final Network RINKEBY = network(GenesisHash.RINKEBY, Forks.RINKEBY);
    public static final Network GOERLI = network(GenesisHash.GOERLI, Forks.GOERLI);
    public static final Network PRIVATE = network(GenesisHash.PRIVATE, Forks.PRIVATE);
    public final String hash;
    public final List<Long> forks;

    public Network(final String hash, final List<Long> forks) {
      this.hash = hash;
      this.forks = forks;
    }

    public static Network network(final String hash, final List<Long> forks) {
      return new Network(hash, forks);
    }
  }

  public static class PeerCheckCase {
    public final String forkIdHash;
    public final long forkIdNext;
    public final boolean want;

    public PeerCheckCase(final String forkIdHash, final long forkIdNext, final boolean want) {
      this.forkIdHash = forkIdHash;
      this.forkIdNext = forkIdNext;
      this.want = want;
    }
  }

  public static ForkId forkId(final String hash, final long next) {
    return new ForkId(Bytes.fromHexString(hash), next);
  }

  public static Optional<ForkId> wantForkId(final String hash, final long next) {
    return Optional.of(forkId(hash, next));
  }

  public static Optional<PeerCheckCase> wantPeerCheck(
      final String hash, final long next, final boolean want) {
    return Optional.of(new PeerCheckCase(hash, next, want));
  }
}
