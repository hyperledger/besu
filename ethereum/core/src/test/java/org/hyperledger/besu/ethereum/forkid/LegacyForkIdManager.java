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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.apache.tuweni.bytes.Bytes;

public class LegacyForkIdManager {

  private final Hash genesisHash;
  private final List<Long> forks;
  private List<ForkId> forkAndHashList;
  private CRC32 crc;
  private Bytes genesisHashCrc;

  public LegacyForkIdManager(final Blockchain blockchain, final List<Long> forks) {
    this.genesisHash = blockchain.getGenesisBlock().getHash();
    // de-dupe and sanitize forks
    this.forks = forks.stream().filter(fork -> fork > 0).distinct().toList();
    createForkIds();
  }

  public List<ForkId> getForkAndHashList() {
    return this.forkAndHashList;
  }

  public ForkId getLatestForkId() {
    if (!forkAndHashList.isEmpty()) {
      return forkAndHashList.getLast();
    }
    return new ForkId(genesisHashCrc, 0);
  }

  public static ForkId readFrom(final RLPInput in) {
    in.enterList();
    final Bytes hash = in.readBytes();
    final Bytes next = in.readBytes();
    in.leaveList();
    return new ForkId(hash, next);
  }

  private void createForkIds() {
    crc = new CRC32();
    crc.update(genesisHash.toArray());
    genesisHashCrc = getCurrentCrcHash(crc);
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
