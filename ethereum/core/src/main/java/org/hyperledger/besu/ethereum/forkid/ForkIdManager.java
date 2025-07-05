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
  private final List<Fork> allForks;
  private final Supplier<BlockHeader> chainHeadSupplier;
  private final long forkNext;
  private final boolean noForksAvailable;
  private final long highestKnownFork;
  private List<ForkId> allForkIds;
  private Bytes genesisHashCrc;

  public ForkIdManager(
      final Blockchain blockchain,
      final List<Long> blockNumberForks,
      final List<Long> timestampForks) {
    checkNotNull(blockchain);
    checkNotNull(blockNumberForks);
    this.chainHeadSupplier = blockchain::getChainHeadHeader;
    try {
      this.genesisHash = blockchain.getGenesisBlock().getHash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    // Create unified fork list from block numbers and timestamps
    final long genesisTimestamp = blockchain.getGenesisBlock().getHeader().getTimestamp();
    final List<Fork> blockForks = blockNumberForks.stream()
        .filter(fork -> fork > 0L)
        .distinct()
        .map(Fork::ofBlockNumber)
        .collect(Collectors.toList());
    final List<Fork> timestampForksFiltered = timestampForks.stream()
        .filter(fork -> fork > genesisTimestamp)
        .distinct()
        .map(Fork::ofTimestamp)
        .collect(Collectors.toList());
    
    this.allForks = Stream.concat(blockForks.stream(), timestampForksFiltered.stream())
        .sorted()
        .collect(Collectors.toUnmodifiableList());
    
    this.forkNext = createForkIds();
    this.noForksAvailable = allForkIds.isEmpty();
    this.highestKnownFork = allForks.isEmpty() ? 0L : allForks.get(allForks.size() - 1).getValue();
  }

  public ForkId getForkIdForChainHead() {
    final BlockHeader header = chainHeadSupplier.get();
    
    // Find the appropriate ForkId by checking each fork against current chain head
    for (final ForkId forkId : allForkIds) {
      if (forkId.getNext() == 0) {
        // This is the final ForkId (no next fork)
        return forkId;
      }
      
      // Find the corresponding fork to determine comparison type
      final Fork correspondingFork = allForks.stream()
          .filter(fork -> fork.getValue() == forkId.getNext())
          .findFirst()
          .orElse(null);
      
      if (correspondingFork != null) {
        final long currentValue = correspondingFork.isBlockNumber() 
            ? header.getNumber() : header.getTimestamp();
        if (currentValue < correspondingFork.getValue()) {
          return forkId;
        }
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
   * @param remoteForkId to be validated.
   * @return boolean (peer valid (true) or invalid (false))
   */
  public boolean peerCheck(final ForkId remoteForkId) {
    if (remoteForkId == null || noForksAvailable) {
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
    if (!isHashKnown(remoteForkId.getHash())) {
      return false;
    }

    final BlockHeader header = chainHeadSupplier.get();
    // Determine if we should compare against block number or timestamp based on forkNext
    final boolean isBlockNumberFork = allForks.stream()
        .anyMatch(fork -> fork.getValue() == forkNext && fork.isBlockNumber());
    final long chainHeadForkValue = isBlockNumberFork ? header.getNumber() : header.getTimestamp();

    return chainHeadForkValue < forkNext
        || (isForkKnown(remoteForkId.getNext())
            && isRemoteAwareOfPresent(remoteForkId.getHash(), remoteForkId.getNext()));
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
    
    // Process all forks in chronological order to build CRC hashes
    for (final Fork fork : allForks) {
      updateCrc(crc, fork.getValue());
      forkHashes.add(getCurrentCrcHash(crc));
    }

    // Create ForkId instances - each hash corresponds to state BEFORE that fork
    // and points to the fork value as "next"
    final List<ForkId> forkIdsList = new ArrayList<>();
    
    for (int i = 0; i < allForks.size(); i++) {
      forkIdsList.add(new ForkId(forkHashes.get(i), allForks.get(i).getValue()));
    }
    
    // Add final ForkId with latest hash and next = 0 (no more forks)
    if (!allForks.isEmpty()) {
      forkIdsList.add(new ForkId(forkHashes.get(forkHashes.size() - 1), 0));
    }
    
    this.allForkIds = forkIdsList;
    
    return allForks.isEmpty() ? 0L : allForks.get(allForks.size() - 1).getValue();
  }

  private static void updateCrc(final CRC32 crc, final long forkValue) {
    final byte[] byteRepresentationFork = EndianUtils.longToBigEndian(forkValue);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
  }

  private static Bytes getCurrentCrcHash(final CRC32 crc) {
    return Bytes.ofUnsignedInt(crc.getValue());
  }
}
