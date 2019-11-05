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

import static org.hyperledger.besu.util.bytes.BytesValue.wrap;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

public class ForkId {

  private Hash genesisHash;
  private CRC32 crc = new CRC32();
  private Long currentHead;
  private Long forkNext;
  private Long highestKnownFork = 0L;
  private ForkIdEntry lastKnownEntry;
//  private boolean useForkId;
  private ArrayDeque<ForkIdEntry> forkAndHashList;

  public ForkId(final Hash genesisHash, final Set<Long> forks, final Long currentHead) {
    this.genesisHash = genesisHash;
    this.currentHead = currentHead;
    if (forks != null) {
//      useForkId = true;
      forkAndHashList = collectForksAndHashes(forks, currentHead);
    } else {
//      useForkId = false;
      forkAndHashList = new ArrayDeque<>();
    }
  };

  public static ForkId buildCollection(
      final Hash genesisHash, final List<Long> forks, final Blockchain blockchain) {
    if (forks == null) {
      return new ForkId(genesisHash, null, blockchain.getChainHeadBlockNumber());
    } else {
      Set<Long> forkSet = new LinkedHashSet<>(forks);
      return new ForkId(genesisHash, forkSet, blockchain.getChainHeadBlockNumber());
    }
  };

  public static ForkId buildCollection(final Hash genesisHash, final List<Long> forks) {
    if (forks == null) {
      return new ForkId(genesisHash, null, Long.MAX_VALUE);
    } else {
      Set<Long> forkSet = new LinkedHashSet<>(forks);
      return new ForkId(genesisHash, forkSet, Long.MAX_VALUE);
    }
  };

  public static ForkId buildCollection(final Hash genesisHash) {
    return new ForkId(genesisHash, null, Long.MAX_VALUE);
  };

  public static ForkIdEntry readFrom(final RLPInput in) {
    in.enterList();
    final String hash = in.readBytesValue(BytesValues::asString);
    final long next = in.readLong();
    in.leaveList();
    return new ForkIdEntry(hash, next);
  }

  // Non-RLP entry (for tests)
  public static ForkIdEntry createIdEntry(final String hash, final long next) {
    return new ForkIdEntry(hash, next);
  }

  public ArrayDeque<ForkIdEntry> getForkAndHashList() {
    return this.forkAndHashList;
  }

  public Hash getLatestForkId() {
    // TODO: implement handling for forkID in status message
//    if (useForkId) {
//      return Hash.fromHexString(lastKnownEntry.hash);
//    } else {
      return genesisHash;
//    }
  }

  public boolean peerCheck(final String forkHash, final Long peerNext) {
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
    if (isHashKnown(forkHash)) {
      if (currentHead < forkNext) {
        return true;
      } else {
        if (isForkKnown(peerNext)) {
          return isRemoteAwareOfPresent(forkHash, peerNext);
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
   * @param peerGenesisOrCheckSumHash
   * @return boolean
   */
  public boolean peerCheck(final Bytes32 peerGenesisOrCheckSumHash) {
    return !peerGenesisOrCheckSumHash.equals(genesisHash);
  }

  private boolean isHashKnown(final String forkHash) {
    for (ForkIdEntry j : forkAndHashList) {
      if (forkHash.equals(j.hash)) {
        return true;
      }
    }
    return false;
  }

  private boolean isForkKnown(final Long nextFork) {
    if (highestKnownFork < nextFork) {
      return true;
    }
    for (ForkIdEntry j : forkAndHashList) {
      if (nextFork.equals(j.next)) {
        return true;
      }
    }
    return false;
  }

  private boolean isRemoteAwareOfPresent(final String forkHash, final Long nextFork) {
    for (ForkIdEntry j : forkAndHashList) {
      if (forkHash.equals(j.hash)) {
        if (nextFork.equals(j.next)) {
          return true;
        } else if (j.next == 0L) {
          return highestKnownFork <= nextFork; // Remote aware of future fork
        } else {
          return false;
        }
      }
    }
    return false;
  }

  private ArrayDeque<ForkIdEntry> collectForksAndHashes(
      final Set<Long> forks, final Long currentHead) {
    boolean first = true;
    ArrayDeque<ForkIdEntry> forkList = new ArrayDeque<>();
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
            new ForkIdEntry(
                updateCrc(this.genesisHash.getHexString()), forkBlockNumber)); // Genesis
        updateCrc(forkBlockNumber);

      } else if (!iterator.hasNext()) {
        // most recent fork
        forkList.add(new ForkIdEntry(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
        lastKnownEntry = new ForkIdEntry(getCurrentCrcHash(), 0L);
        forkList.add(lastKnownEntry);
        if (currentHead > forkBlockNumber) {
          this.forkNext = 0L;
        } else {
          this.forkNext = forkBlockNumber;
        }

      } else {
        forkList.add(new ForkIdEntry(getCurrentCrcHash(), forkBlockNumber));
        updateCrc(forkBlockNumber);
      }
    }
    return forkList;
  }

  private String updateCrc(final Long block) {
    byte[] byteRepresentationFork = longToBigEndian(block);
    crc.update(byteRepresentationFork, 0, byteRepresentationFork.length);
    return getCurrentCrcHash();
  }

  private String updateCrc(final String hash) {
    byte[] byteRepresentation = hexStringToByteArray(hash);
    crc.update(byteRepresentation, 0, byteRepresentation.length);
    return getCurrentCrcHash();
  }

  public String getCurrentCrcHash() {
    return "0x" + encodeHexString(BytesValues.ofUnsignedInt(crc.getValue()).getByteArray());
  }

  // TODO use Hash class instead of string for checksum.  convert to or from string only when needed
  public static class ForkIdEntry {
    String hash;
    long next;

    public ForkIdEntry(final String hash, final long next) {
      this.hash = hash;
      this.next = next;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      out.writeBytesValue(wrap(hash.getBytes(StandardCharsets.US_ASCII)));
      out.writeLong(next);
      out.endList();
    }

    public List<byte[]> asByteList() {
      ArrayList<byte[]> forRLP = new ArrayList<byte[]>();
      forRLP.add(hexStringToByteArray(hash));
      forRLP.add(longToBigEndian(next));
      return forRLP;
    }

    public List<ForkIdEntry> asList() {
      ArrayList<ForkIdEntry> forRLP = new ArrayList<>();
      forRLP.add(this);
      return forRLP;
    }

    @Override
    public String toString() {
      return "IdEntry(hash=" + this.hash + ", next=" + this.next + ")";
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj instanceof ForkIdEntry) {
        ForkIdEntry other = (ForkIdEntry) obj;
        return other.hash.equals(this.hash) && other.next == this.next;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }
  }

  // TODO: Ask / look to see if there is a helper for these below <----------
  private static byte[] hexStringToByteArray(final String s) {
    String string = "";
    if (s.startsWith("0x")) {
      string = s.replaceFirst("0x", "");
    }
    string = (string.length() % 2 == 0 ? "" : "0") + string;
    return decodeHexString(string);
  }

  // next three methods adopted from:
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

  private static String byteToHex(final byte num) {
    char[] hexDigits = new char[2];
    hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
    hexDigits[1] = Character.forDigit((num & 0xF), 16);
    return new String(hexDigits);
  }

  private static String encodeHexString(final byte[] byteArray) {
    StringBuilder hexStringBuffer = new StringBuilder();
    for (int i = 0; i < byteArray.length; i++) {
      hexStringBuffer.append(byteToHex(byteArray[i]));
    }
    return hexStringBuffer.toString();
  }

  private static byte[] decodeHexString(final String hexString) {
    if (hexString.length() % 2 == 1) {
      throw new IllegalArgumentException("Invalid hexadecimal String supplied.");
    }

    byte[] bytes = new byte[hexString.length() / 2];
    for (int i = 0; i < hexString.length(); i += 2) {
      bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
    }
    return bytes;
  }

  private static byte hexToByte(final String hexString) {
    int firstDigit = toDigit(hexString.charAt(0));
    int secondDigit = toDigit(hexString.charAt(1));
    return (byte) ((firstDigit << 4) + secondDigit);
  }

  private static int toDigit(final char hexChar) {
    int digit = Character.digit(hexChar, 16);
    if (digit == -1) {
      throw new IllegalArgumentException("Invalid Hexadecimal Character: " + hexChar);
    }
    return digit;
  }
}
