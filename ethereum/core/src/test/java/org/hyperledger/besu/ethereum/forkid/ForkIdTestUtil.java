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
package org.hyperledger.besu.ethereum.forkid;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;

public class ForkIdTestUtil {

  public static Blockchain mockBlockchain(
      final String genesisHash, final long chainHeight, final long timestamp) {
    return mockBlockchain(genesisHash, () -> chainHeight, timestamp);
  }

  public static Blockchain mockBlockchain(
      final String genesisHash, final LongSupplier chainHeightSupplier, final long timestamp) {
    final Blockchain mockchain = mock(Blockchain.class);
    final BlockHeader mockHeader = mock(BlockHeader.class);
    final Block block = spy(new Block(mockHeader, null));
    final BlockHeader mockChainHeadHeader = mock(BlockHeader.class);
    when(mockchain.getGenesisBlock()).thenReturn(block);
    when(mockchain.getChainHeadBlockNumber()).thenReturn(chainHeightSupplier.getAsLong());
    when(mockHeader.getHash()).thenReturn(Hash.fromHexString(genesisHash));
    when(mockchain.getChainHeadHeader()).thenReturn(mockChainHeadHeader);
    when(mockChainHeadHeader.getNumber()).thenReturn(chainHeightSupplier.getAsLong());
    when(mockChainHeadHeader.getTimestamp()).thenReturn(timestamp);
    final BlockHeader mockGenesisBlockHeader = mock(BlockHeader.class);
    when(block.getHeader()).thenReturn(mockGenesisBlockHeader);
    when(mockGenesisBlockHeader.getTimestamp()).thenReturn(1L);
    return mockchain;
  }

  public static class GenesisHash {
    public static final String MAINNET =
        "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";
    public static final String SEPOLIA =
        "0x25a5cc106eea7138acab33231d7160d69cb777ee0c2c553fcddf5138993e6dd9";
    public static final String PRIVATE =
        "0x0000000000000000000000000000000000000000000000000000000000000000";
  }

  public static class Forks {
    public static final List<Long> MAINNET =
        Arrays.asList(
            1920000L, 1150000L, 2463000L, 2675000L, 2675000L, 4370000L, 7280000L, 7280000L,
            9069000L, 9200000L, 12244000L, 12965000L, 13773000L, 15050000L);
    public static final List<Long> SEPOLIA_BLOCKNUMBERS =
        Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1735371L);

    public static final List<Long> SEPOLIA_TIMESTAMPS = Arrays.asList(1677557088L);

    public static final List<Long> PRIVATE = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

    public static final List<Long> MAINNET_WITH_SHANGHAI_BLOCKS =
        Streams.concat(MAINNET.stream(), Stream.of(18000000L)).collect(Collectors.toList());

    public static final List<Long> MAINNET_WITH_SHANGHAI_TIMESTAMPS = List.of(1668000000L);
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
    public static final List<ForkId> SEPOLIA =
        Arrays.asList(
            new ForkId(Bytes.fromHexString("0xfe3366e7"), 1735371L),
            new ForkId(Bytes.fromHexString("0xb96cbd13"), 1677557088L),
            new ForkId(Bytes.fromHexString("0xf7f9bc08"), 0L)); // First Shanghai block (timestamp)

    public static final List<ForkId> WITHDRAWALS =
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
            new ForkId(Bytes.fromHexString("0xf0afd0e3"), 18000000L), // First Arrow Glacier block
            new ForkId(Bytes.fromHexString("0x4fb8a872"), 1668000000L), // First Merge Start block
            new ForkId(Bytes.fromHexString("0xc1fdf181"), 0L) // First Shanghai block
            );
  }

  public static class Network {
    public static final Network MAINNET = network(GenesisHash.MAINNET, Forks.MAINNET, emptyList());
    public static final Network SEPOLIA =
        network(GenesisHash.SEPOLIA, Forks.SEPOLIA_BLOCKNUMBERS, Forks.SEPOLIA_TIMESTAMPS);
    public static final Network PRIVATE = network(GenesisHash.PRIVATE, Forks.PRIVATE, emptyList());

    public static final Network MAINNET_WITH_SHANGHAI =
        network(
            GenesisHash.MAINNET,
            Forks.MAINNET_WITH_SHANGHAI_BLOCKS,
            Forks.MAINNET_WITH_SHANGHAI_TIMESTAMPS);

    public final String hash;
    public final List<Long> blockForks;
    public final List<Long> timestampForks;

    public Network(
        final String hash, final List<Long> blockForks, final List<Long> timestampForks) {
      this.hash = hash;
      this.blockForks = blockForks;
      this.timestampForks = timestampForks;
    }

    public static Network network(
        final String hash, final List<Long> blockForks, final List<Long> timestampForks) {
      return new Network(hash, blockForks, timestampForks);
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
