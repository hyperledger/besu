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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.ForkId;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class LegacyForkIdManager {

  private final Blockchain blockchain;
  private final Hash genesisHash;
  private final List<Long> forks;
  private long forkNext;
  private final long highestKnownFork;
  private List<ForkId> forkAndHashList;

  public LegacyForkIdManager(final Blockchain blockchain, final List<Long> forks) {
    this.blockchain = blockchain;
    this.genesisHash = blockchain.getGenesisBlock().getHash();
    // de-dupe and sanitize forks
    this.forks =
        forks.stream().filter(fork -> fork > 0).distinct().collect(Collectors.toUnmodifiableList());
    highestKnownFork = forks.size() > 0 ? forks.get(forks.size() - 1) : 0L;
    createForkIds();
  };

  public List<ForkId> getForkAndHashList() {
    return this.forkAndHashList;
  }

  public ForkId getLatestForkId() {
    if (forkAndHashList.size() > 0) {
      return forkAndHashList.get(forkAndHashList.size() - 1);
    }
    return null;
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
    if (forkId == null) {
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
    if (isHashKnown(forkId.getHash())) {
      if (blockchain.getChainHeadBlockNumber() < forkNext) {
        return true;
      } else {
        if (isForkKnown(forkId.getNext())) {
          return isRemoteAwareOfPresent(forkId.getHash(), forkId.getNext());
        } else {
          return false;
        }
      }
    } else {
      return false;
    }
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
    return forkAndHashList.stream().map(ForkId::getHash).anyMatch(hash -> hash.equals(forkHash));
  }

  private boolean isForkKnown(final Long nextFork) {
    return highestKnownFork < nextFork
        || forkAndHashList.stream().map(ForkId::getNext).anyMatch(fork -> fork.equals(nextFork));
  }

  private boolean isRemoteAwareOfPresent(final Bytes forkHash, final Long nextFork) {
    for (final ForkId j : forkAndHashList) {
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

  private void createForkIds() {
    final CRC32 crc = new CRC32();
    crc.update(genesisHash.toArray());
    final List<Bytes> forkHashes = new ArrayList<>(List.of(getCurrentCrcHash(crc)));
    for (final Long fork : forks) {
      updateCrc(crc, fork);
      forkHashes.add(getCurrentCrcHash(crc));
    }
    final List<ForkId> forkIds = new ArrayList<>();
    // This loop is for all the fork hashes that have an associated "next fork"
    for (int i = 0; i < forks.size(); i++) {
      forkIds.add(new ForkId(forkHashes.get(i), forks.get(i)));
    }
    if (!forks.isEmpty()) {
      forkNext = forkIds.get(forkIds.size() - 1).getNext();
      forkIds.add(new ForkId(forkHashes.get(forkHashes.size() - 1), 0));
    }
    this.forkAndHashList = forkIds;
    System.out.println(this.forkAndHashList);
  }

  private void updateCrc(final CRC32 crc, final Long block) {
    final byte[] byteRepresentationFork = longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
  }

  private Bytes getCurrentCrcHash(final CRC32 crc) {
    return Bytes.ofUnsignedInt(crc.getValue());
  }

  // next two methods adopted from:
  // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/util/Pack.java
  private static byte[] longToBigEndian(final long n) {
    final byte[] bs = new byte[8];
    intToBigEndian((int) (n >>> 32), bs, 0);
    intToBigEndian((int) (n & 0xffffffffL), bs, 4);
    return bs;
  }

  @SuppressWarnings("MethodInputParametersMustBeFinal")
  private static void intToBigEndian(final int n, final byte[] bs, int off) {
    bs[off] = (byte) (n >>> 24);
    bs[++off] = (byte) (n >>> 16);
    bs[++off] = (byte) (n >>> 8);
    bs[++off] = (byte) (n);
  }
}
