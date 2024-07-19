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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.util.EndianUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class ForkIdManager {

  private final Hash genesisHash;
  private final List<ForkId> blockNumbersForkIds;
  private final List<ForkId> timestampsForkIds;
  private final List<ForkId> allForkIds;

  private final List<Long> blockNumberForks;
  private final List<Long> timestampForks;

  private final Supplier<BlockHeader> chainHeadSupplier;
  private final long forkNext;
  private final boolean noForksAvailable;
  private final long highestKnownFork;
  private Bytes genesisHashCrc;
  private final boolean legacyEth64;

  public ForkIdManager(
      final Blockchain blockchain,
      final List<Long> blockNumberForks,
      final List<Long> timestampForks,
      final boolean legacyEth64) {
    checkNotNull(blockchain);
    checkNotNull(blockNumberForks);
    this.chainHeadSupplier = blockchain::getChainHeadHeader;
    try {
      this.genesisHash = blockchain.getGenesisBlock().getHash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.blockNumbersForkIds = new ArrayList<>();
    this.timestampsForkIds = new ArrayList<>();
    this.legacyEth64 = legacyEth64;
    this.blockNumberForks =
        blockNumberForks.stream()
            .filter(fork -> fork > 0L)
            .distinct()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    this.timestampForks =
        timestampForks.stream()
            .filter(fork -> fork > blockchain.getGenesisBlock().getHeader().getTimestamp())
            .distinct()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    final List<Long> allForkNumbers =
        Stream.concat(blockNumberForks.stream(), timestampForks.stream()).toList();
    this.forkNext = createForkIds();
    this.allForkIds =
        Stream.concat(blockNumbersForkIds.stream(), timestampsForkIds.stream())
            .collect(Collectors.toList());
    this.noForksAvailable = allForkIds.isEmpty();
    this.highestKnownFork =
        !allForkNumbers.isEmpty() ? allForkNumbers.get(allForkNumbers.size() - 1) : 0L;
  }

  public ForkId getForkIdForChainHead() {
    if (legacyEth64) {
      return blockNumbersForkIds.isEmpty()
          ? new ForkId(genesisHashCrc, 0)
          : blockNumbersForkIds.get(blockNumbersForkIds.size() - 1);
    }
    final BlockHeader header = chainHeadSupplier.get();
    for (final ForkId forkId : blockNumbersForkIds) {
      if (header.getNumber() < forkId.getNext()) {
        return forkId;
      }
    }
    for (final ForkId forkId : timestampsForkIds) {
      if (header.getTimestamp() < forkId.getNext()) {
        return forkId;
      }
    }
    return allForkIds.isEmpty()
        ? new ForkId(genesisHashCrc, 0)
        : allForkIds.get(allForkIds.size() - 1);
  }

  @VisibleForTesting
  public List<ForkId> getAllForkIds() {
    return allForkIds;
  }

  public static ForkId readFrom(final RLPInput in) {
    in.enterList();
    final Bytes hash = in.readBytes();
    final Bytes next = in.readBytes();
    in.leaveList();
    return new ForkId(hash, next);
  }

  /**
   * EIP-2124 behaviour
   *
   * @param forkId to be validated.
   * @return boolean (peer valid (true) or invalid (false))
   */
  public boolean peerCheck(final ForkId forkId) {
    if (forkId == null || noForksAvailable) {
      return true; // Another method must be used to validate (i.e. genesis hash)
    }
    // Run the fork checksum validation rule set:
    //   1. If local and remote FORK_CSUM matches, connect.
    //        The two nodes are in the same fork state currently. They might know
    //        of differing future forks, but that's not relevant until the fork
    //        triggers (might be postponed, nodes might be updated to match).
    //   2. If the remote FORK_CSUM is a subset of the local past forks and the
    //      remote FORK_NEXT matches with the locally following fork block number,
    //      connect.
    //        Remote node is currently syncing. It might eventually diverge from
    //        us, but at this current point in time we don't have enough information.
    //   3. If the remote FORK_CSUM is a superset of the local past forks and can
    //      be completed with locally known future forks, connect.
    //        Local node is currently syncing. It might eventually diverge from
    //        the remote, but at this current point in time we don't have enough
    //        information.
    //   4. Reject in all other cases.
    if (!isHashKnown(forkId.getHash())) {
      return false;
    }

    final BlockHeader header = chainHeadSupplier.get();
    final long forkValue =
        blockNumberForks.contains(forkNext) ? header.getNumber() : header.getTimestamp();

    return forkValue < forkNext
        || (isForkKnown(forkId.getNext())
            && isRemoteAwareOfPresent(forkId.getHash(), forkId.getNext()));
  }

  /**
   * Non EIP-2124 behaviour
   *
   * @param peerGenesisHash Hash to be validated.
   * @return boolean
   */
  public boolean peerCheck(final Bytes32 peerGenesisHash) {
    return !peerGenesisHash.equals(genesisHash);
  }

  private boolean isHashKnown(final Bytes forkHash) {
    return allForkIds.stream().map(ForkId::getHash).anyMatch(hash -> hash.equals(forkHash));
  }

  private boolean isForkKnown(final Long nextFork) {
    return highestKnownFork < nextFork
        || allForkIds.stream().map(ForkId::getNext).anyMatch(fork -> fork.equals(nextFork));
  }

  private boolean isRemoteAwareOfPresent(final Bytes forkHash, final Long nextFork) {
    for (final ForkId j : getAllForkIds()) {
      if (forkHash.equals(j.getHash())) {
        if (nextFork.equals(j.getNext())) {
          return true;
        } else if (j.getNext() == 0L) {
          return highestKnownFork <= nextFork; // Remote aware of an additional future fork
        } else {
          return false;
        }
      }
    }
    return false;
  }

  private long createForkIds() {
    final CRC32 crc = new CRC32();
    crc.update(genesisHash.toArray());
    genesisHashCrc = getCurrentCrcHash(crc);
    final List<Bytes> forkHashes = new ArrayList<>(List.of(genesisHashCrc));
    blockNumberForks.forEach(
        fork -> {
          updateCrc(crc, fork);
          forkHashes.add(getCurrentCrcHash(crc));
        });

    timestampForks.forEach(
        fork -> {
          updateCrc(crc, fork);
          forkHashes.add(getCurrentCrcHash(crc));
        });

    // This loop is for all the fork hashes that have an associated "next fork"
    for (int i = 0; i < blockNumberForks.size(); i++) {
      blockNumbersForkIds.add(new ForkId(forkHashes.get(i), blockNumberForks.get(i)));
    }
    for (int i = 0; i < timestampForks.size(); i++) {
      timestampsForkIds.add(
          new ForkId(forkHashes.get(blockNumberForks.size() + i), timestampForks.get(i)));
    }
    long forkNext = 0;
    if (!timestampForks.isEmpty()) {
      forkNext = timestampForks.get(timestampForks.size() - 1);
      timestampsForkIds.add(new ForkId(forkHashes.get(forkHashes.size() - 1), 0));
    } else if (!blockNumberForks.isEmpty()) {
      forkNext = blockNumbersForkIds.get(blockNumbersForkIds.size() - 1).getNext();
      blockNumbersForkIds.add(new ForkId(forkHashes.get(forkHashes.size() - 1), 0));
    }
    return forkNext;
  }

  private static void updateCrc(final CRC32 crc, final Long block) {
    final byte[] byteRepresentationFork = EndianUtils.longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
  }

  private static Bytes getCurrentCrcHash(final CRC32 crc) {
    return Bytes.ofUnsignedInt(crc.getValue());
  }
}
