/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

/** A mined Ethereum block header. */
public class LightBlockHeader extends BlockHeader {

  protected final Hash hash;

  protected Bytes rlp;

  public LightBlockHeader(
      final Hash parentHash,
      final Hash hash,
      final Difficulty difficulty,
      final long number,
      final Bytes rlp) {
    super(
        parentHash,
        null,
        null,
        null,
        null,
        null,
        null,
        difficulty,
        number,
        -1,
        -1,
        -1,
        null,
        null,
        null,
        -1,
        null);
    this.rlp = rlp;
    this.hash = hash;
  }

  public static LightBlockHeader readFrom(final RLPInput input) {
    Bytes raw = input.raw();
    input.reset();
    input.enterList();
    final Hash parentHash = Hash.wrap(input.readBytes32());
    input.skipNext();
    input.skipNext();
    input.skipNext();
    input.skipNext();
    input.skipNext();
    final Difficulty difficulty = Difficulty.of(input.readUInt256Scalar());
    final long number = input.readLongScalar();
    input.leaveList();
    return new LightBlockHeader(parentHash, Hash.hash(raw), difficulty, number, raw);
  }

  @Override
  public Hash getHash() {
    return hash;
  }

  @Override
  public Bytes getRlp() {
    return rlp;
  }
}
