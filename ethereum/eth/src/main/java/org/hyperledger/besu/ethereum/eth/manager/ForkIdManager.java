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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.EndianUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class ForkIdManager {

  private final Hash genesisHash;
  private final List<ForkId> forkIds;

  private final List<Long> forkBlockNumbers;
  private final LongSupplier chainHeadSupplier;
  private final long forkNext;
  private final boolean onlyZerosForkBlocks;
  private final long highestKnownFork;
  private Bytes genesisHashCrc;
  private final boolean legacyEth64;

  public ForkIdManager(
      final Blockchain blockchain, final List<Long> nonFilteredForks, final boolean legacyEth64) {
    checkNotNull(blockchain);
    checkNotNull(nonFilteredForks);
    this.chainHeadSupplier = blockchain::getChainHeadBlockNumber;
    this.genesisHash = blockchain.getGenesisBlock().getHash();
    this.forkIds = new ArrayList<>();
    this.legacyEth64 = legacyEth64;
    this.forkBlockNumbers =
        nonFilteredForks.stream()
            .filter(fork -> fork > 0L)
            .distinct()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    this.onlyZerosForkBlocks = nonFilteredForks.stream().allMatch(value -> 0L == value);
    this.forkNext = createForkIds();
    this.highestKnownFork =
        !forkBlockNumbers.isEmpty() ? forkBlockNumbers.get(forkBlockNumbers.size() - 1) : 0L;
  }

  public ForkId getForkIdForChainHead() {
    if (legacyEth64) {
      return forkIds.isEmpty() ? null : forkIds.get(forkIds.size() - 1);
    }
    final long head = chainHeadSupplier.getAsLong();
    for (final ForkId forkId : forkIds) {
      if (head < forkId.getNext()) {
        return forkId;
      }
    }
    return forkIds.isEmpty() ? new ForkId(genesisHashCrc, 0) : forkIds.get(forkIds.size() - 1);
  }

  @VisibleForTesting
  List<ForkId> getForkIds() {
    return this.forkIds;
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
  boolean peerCheck(final ForkId forkId) {
    if (forkId == null || onlyZerosForkBlocks) {
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
    return chainHeadSupplier.getAsLong() < forkNext
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
    return forkIds.stream().map(ForkId::getHash).anyMatch(hash -> hash.equals(forkHash));
  }

  private boolean isForkKnown(final Long nextFork) {
    return highestKnownFork < nextFork
        || forkIds.stream().map(ForkId::getNext).anyMatch(fork -> fork.equals(nextFork));
  }

  private boolean isRemoteAwareOfPresent(final Bytes forkHash, final Long nextFork) {
    for (final ForkId j : forkIds) {
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
    forkBlockNumbers.forEach(
        fork -> {
          updateCrc(crc, fork);
          forkHashes.add(getCurrentCrcHash(crc));
        });

    // This loop is for all the fork hashes that have an associated "next fork"
    for (int i = 0; i < forkBlockNumbers.size(); i++) {
      forkIds.add(new ForkId(forkHashes.get(i), forkBlockNumbers.get(i)));
    }
    long forkNext = 0;
    if (!forkBlockNumbers.isEmpty()) {
      forkNext = forkIds.get(forkIds.size() - 1).getNext();
      forkIds.add(new ForkId(forkHashes.get(forkHashes.size() - 1), 0));
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
