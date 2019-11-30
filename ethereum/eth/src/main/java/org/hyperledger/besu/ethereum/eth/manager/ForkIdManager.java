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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;

public class ForkIdManager {

  private Hash genesisHash;
  private CRC32 crc = new CRC32();
  private Long currentHead;
  private Long forkNext;
  private Long highestKnownFork = 0L;
  private ForkId lastKnownEntry = null;
  private ArrayDeque<ForkId> forkAndHashList;

  public ForkIdManager(final Hash genesisHash, final List<Long> forks, final Long currentHead) {
    this.genesisHash = genesisHash;
    this.currentHead = currentHead;
    if (forks != null) {
      forkAndHashList = collectForksAndHashes(forks, currentHead);
    } else {
      forkAndHashList = new ArrayDeque<>();
    }
  };

  static ForkIdManager buildCollection(
      final Hash genesisHash, final List<Long> forks, final Blockchain blockchain) {
    if (forks == null) {
      return new ForkIdManager(genesisHash, null, blockchain.getChainHeadBlockNumber());
    } else {
      return new ForkIdManager(genesisHash, forks, blockchain.getChainHeadBlockNumber());
    }
  };

  public static ForkIdManager buildCollection(final Hash genesisHash, final List<Long> forks) {
    if (forks == null) {
      return new ForkIdManager(genesisHash, null, Long.MAX_VALUE);
    } else {
      return new ForkIdManager(genesisHash, forks, Long.MAX_VALUE);
    }
  };

  static ForkIdManager buildCollection(final Hash genesisHash) {
    return new ForkIdManager(genesisHash, null, Long.MAX_VALUE);
  };

  // Non-generated entry (for tests)
  public static ForkId createIdEntry(final String hash, final long next) {
    return new ForkId(hash, next);
  }

  // Non-generated entry (for tests)
  public static ForkId createIdEntry(final String hash, final String next) {
    return new ForkId(hash, next);
  }

  public ArrayDeque<ForkId> getForkAndHashList() {
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
    for (ForkId j : forkAndHashList) {
      if (forkHash.equals(j.getHash())) {
        return true;
      }
    }
    return false;
  }

  private boolean isForkKnown(final Long nextFork) {
    if (highestKnownFork < nextFork) {
      return true;
    }
    for (ForkId j : forkAndHashList) {
      if (nextFork.equals(j.getNext())) {
        return true;
      }
    }
    return false;
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

  // TODO: should sort these when first gathering the list of forks to ensure order
  private ArrayDeque<ForkId> collectForksAndHashes(final List<Long> forks, final Long currentHead) {
    boolean first = true;
    ArrayDeque<ForkId> forkList = new ArrayDeque<>();
    Iterator<Long> iterator = forks.iterator();
    while (iterator.hasNext()) {
      Long forkBlockNumber = iterator.next();
      if (highestKnownFork < forkBlockNumber) {
        highestKnownFork = forkBlockNumber;
      }
      if (first) {
        // first fork
        first = false;
        forkList.add(
            new ForkId(updateCrc(this.genesisHash.getHexString()), forkBlockNumber)); // Genesis
        updateCrc(forkBlockNumber);

      } else if (!iterator.hasNext()) {
        // most recent fork
        forkList.add(new ForkId(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
        // next fork or no future fork known
        if (currentHead > forkBlockNumber) {
          lastKnownEntry = new ForkId(getCurrentCrcHash(), 0L);
          forkList.add(lastKnownEntry);
          this.forkNext = 0L;
        } else {
          lastKnownEntry = new ForkId(getCurrentCrcHash(), forkBlockNumber);
          forkList.add(lastKnownEntry);
          this.forkNext = forkBlockNumber;
        }

      } else {
        forkList.add(new ForkId(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
      }
    }
    return forkList;
  }

  private void updateCrc(final Long block) {
    byte[] byteRepresentationFork = longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
  }

  private BytesValue updateCrc(final String hash) {
    BytesValue bv = BytesValue.fromHexString(hash);
    byte[] byteRepresentation = bv.extractArray();
    crc.update(byteRepresentation, 0, byteRepresentation.length);
    return getCurrentCrcHash();
  }

  private BytesValue getCurrentCrcHash() {
    return BytesValues.ofUnsignedInt(crc.getValue());
  }

  public static class ForkId {
    BytesValue hash;
    BytesValue next;
    BytesValue forkIdRLP;

    ForkId(final BytesValue hash, final BytesValue next) {
      this.hash = hash;
      this.next = next;
      createForkIdRLP();
    }

    ForkId(final String hash, final String next) {
      this.hash = BytesValue.fromHexString((hash.length() % 2 == 0 ? "" : "0") + hash);
      if (this.hash.size() < 4) {
        this.hash = padToEightBytes(this.hash);
      }
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

    // Non-RLP entry (for tests)
    public BytesValue createNotAsListForkIdRLP() {
      BytesValueRLPOutput outPlain = new BytesValueRLPOutput();
      outPlain.startList();
      outPlain.writeBytesValue(hash);
      outPlain.writeBytesValue(next);
      outPlain.endList();
      return outPlain.encoded();
    }

    public static ForkId readFrom(final RLPInput in) {
      in.enterList();
      final BytesValue hash = in.readBytesValue();
      final long next = in.readLong();
      in.leaveList();
      return new ForkId(hash, next);
    }

    public List<byte[]> asByteList() {
      ArrayList<byte[]> forRLP = new ArrayList<byte[]>();
      forRLP.add(hash.getByteArray());
      forRLP.add(next.getByteArray());
      return forRLP;
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
        Long thisNext = BytesValues.extractLong(next);
        return other.getHash().equals(this.hash) && thisNext.equals(other.getNext());
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
