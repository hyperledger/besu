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

import static java.util.Collections.emptyList;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import com.google.common.annotations.VisibleForTesting;

public class ForkIdManager {

  private Hash genesisHash;
  private Long currentHead;
  private Long forkNext;
  private Long highestKnownFork = 0L;
  private ForkId lastKnownEntry = null;
  private List<ForkId> forkAndHashList;

  public ForkIdManager(final Hash genesisHash, final List<Long> forks, final Long currentHead) {
    this.genesisHash = genesisHash;
    this.currentHead = currentHead;
    this.forkAndHashList =
        createForkIds(
            // If there are two forks at the same block height, we only want to add it once to the
            // crc checksum
            forks.stream().distinct().collect(Collectors.toUnmodifiableList()));
  };

  static ForkIdManager buildCollection(
      final Hash genesisHash, final List<Long> forks, final Blockchain blockchain) {
    return new ForkIdManager(genesisHash, forks, blockchain.getChainHeadBlockNumber());
  };

  @VisibleForTesting
  public static ForkIdManager buildCollection(final Hash genesisHash, final List<Long> forks) {
    return new ForkIdManager(genesisHash, forks, Long.MAX_VALUE);
  };

  static ForkIdManager buildCollection(final Hash genesisHash) {
    return new ForkIdManager(genesisHash, emptyList(), Long.MAX_VALUE);
  };

  // Non-generated entry (for tests)
  public static ForkId createIdEntry(final String hash, final long next) {
    return new ForkId(hash, next);
  }

  // Non-generated entry (for tests)
  public static ForkId createIdEntry(final String hash, final String next) {
    return new ForkId(hash, next);
  }

  public List<ForkId> getForkAndHashList() {
    return this.forkAndHashList;
  }

  ForkId getLatestForkId() {
    return lastKnownEntry;
  }

  public static ForkId readFrom(final RLPInput in) {
    in.enterList();
    final BytesValue hash = in.readBytesValue();
    final BytesValue next = in.readBytesValue();
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
    if (forkId == null) {
      return true; // Another method must be used to validate (i.e. genesis hash)
    }
    // Run the fork checksum validation ruleset:
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
      if (currentHead < forkNext) {
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

  private boolean isHashKnown(final BytesValue forkHash) {
    return forkAndHashList.stream().map(ForkId::getHash).anyMatch(hash -> hash.equals(forkHash));
  }

  private boolean isForkKnown(final Long nextFork) {
    return highestKnownFork < nextFork
        || forkAndHashList.stream().map(ForkId::getNext).anyMatch(fork -> fork.equals(nextFork));
  }

  private boolean isRemoteAwareOfPresent(final BytesValue forkHash, final Long nextFork) {
    for (ForkId j : forkAndHashList) {
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

  private List<ForkId> createForkIds(final List<Long> forks) {
    final CRC32 crc = new CRC32();
    crc.update(this.genesisHash.getByteArray());
    final List<BytesValue> forkHashes = new ArrayList<>(List.of(getCurrentCrcHash(crc)));
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
      this.forkNext = forkIds.get(forkIds.size() - 1).getNext();
      forkIds.add(
          new ForkId(
              forkHashes.get(forkHashes.size() - 1),
              currentHead > forkNext ? 0 : forkNext // Use 0 if there are no known next forks
              ));
    }
    return forkIds;
  }

  private void updateCrc(final CRC32 crc, final Long block) {
    byte[] byteRepresentationFork = longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
  }

  private BytesValue getCurrentCrcHash(final CRC32 crc) {
    return BytesValues.ofUnsignedInt(crc.getValue());
  }

  public static class ForkId {
    final BytesValue hash;
    final BytesValue next;
    BytesValue forkIdRLP;

    ForkId(final BytesValue hash, final BytesValue next) {
      this.hash = hash;
      this.next = next;
      createForkIdRLP();
    }

    ForkId(final String hash, final String next) {
      this.hash =
          padToEightBytes(BytesValue.fromHexString((hash.length() % 2 == 0 ? "" : "0") + hash));
      if (next.equals("") || next.equals("0x")) {
        this.next = BytesValue.EMPTY;
      } else if (next.startsWith("0x")) {
        long asLong = Long.parseLong(next.replaceFirst("0x", ""), 16);
        this.next = BytesValues.trimLeadingZeros(BytesValue.wrap(longToBigEndian(asLong)));
      } else {
        this.next = BytesValue.wrap(longToBigEndian(Long.parseLong(next)));
      }
      createForkIdRLP();
    }

    ForkId(final String hash, final long next) {
      this.hash = BytesValue.fromHexString(hash);
      this.next = BytesValue.wrap(longToBigEndian(next));
      createForkIdRLP();
    }

    ForkId(final BytesValue hash, final long next) {
      this.hash = hash;
      this.next = BytesValue.wrap(longToBigEndian(next));
      createForkIdRLP();
    }

    public long getNext() {
      return BytesValues.extractLong(next);
    }

    public BytesValue getHash() {
      return hash;
    }

    void createForkIdRLP() {
      BytesValueRLPOutput out = new BytesValueRLPOutput();
      writeTo(out);
      forkIdRLP = out.encoded();
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      out.writeBytesValue(hash);
      out.writeBytesValue(next);
      out.endList();
    }

    public static ForkId readFrom(final RLPInput in) {
      in.enterList();
      final BytesValue hash = in.readBytesValue();
      final long next = in.readLong();
      in.leaveList();
      return new ForkId(hash, next);
    }

    public List<ForkId> asList() {
      ArrayList<ForkId> forRLP = new ArrayList<>();
      forRLP.add(this);
      return forRLP;
    }

    private static BytesValue padToEightBytes(final BytesValue hash) {
      if (hash.size() < 4) {
        BytesValue padded = BytesValues.concatenate(hash, BytesValue.fromHexString("0x00"));
        return padToEightBytes(padded);
      } else {
        return hash;
      }
    }

    @Override
    public String toString() {
      return "ForkId(hash=" + this.hash + ", next=" + BytesValues.extractLong(next) + ")";
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj instanceof ForkId) {
        ForkId other = (ForkId) obj;
        long thisNext = BytesValues.extractLong(next);
        return other.getHash().equals(this.hash) && thisNext == other.getNext();
      }
      return false;
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }
  }

  // next two methods adopted from:
  // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/util/Pack.java
  private static byte[] longToBigEndian(final long n) {
    byte[] bs = new byte[8];
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
