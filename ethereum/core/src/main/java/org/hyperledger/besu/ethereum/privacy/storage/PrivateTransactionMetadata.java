/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

/** Mined private transaction metadata. */
public class PrivateTransactionMetadata {
  private final Hash privateMarkerTransactionHash;
  private final Hash stateRoot;

  public PrivateTransactionMetadata(final Hash privateMarkerTransactionHash, final Hash stateRoot) {
    this.privateMarkerTransactionHash = privateMarkerTransactionHash;
    this.stateRoot = stateRoot;
  }

  public Hash getStateRoot() {
    return stateRoot;
  }

  public Hash getPrivateMarkerTransactionHash() {
    return privateMarkerTransactionHash;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytes(privateMarkerTransactionHash);
    out.writeBytes(stateRoot);

    out.endList();
  }

  public static PrivateTransactionMetadata readFrom(final RLPInput input) {
    input.enterList();

    final PrivateTransactionMetadata privateTransactionMetadata =
        new PrivateTransactionMetadata(
            Hash.wrap(input.readBytes32()), Hash.wrap(input.readBytes32()));

    input.leaveList();
    return privateTransactionMetadata;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivateTransactionMetadata that = (PrivateTransactionMetadata) o;
    return privateMarkerTransactionHash.equals(that.privateMarkerTransactionHash)
        && stateRoot.equals(that.stateRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(privateMarkerTransactionHash, stateRoot);
  }
}
