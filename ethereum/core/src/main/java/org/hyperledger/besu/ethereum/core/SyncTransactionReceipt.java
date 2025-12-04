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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

/** A transaction receipt representation used for syncing. */
public class SyncTransactionReceipt {

  private final Bytes rlpBytes;

  /**
   * Creates an instance of a receipt that just contains the rlp bytes.
   *
   * @param rlpBytes bytes of the RLP-encoded transaction receipt
   */
  public SyncTransactionReceipt(final Bytes rlpBytes) {
    this.rlpBytes = rlpBytes;
  }

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @return the transaction receipt
   */
  public static SyncTransactionReceipt readFrom(final RLPInput rlpInput) {
    SyncTransactionReceipt ret;
    if (rlpInput.nextIsList()) {
      ret = new SyncTransactionReceipt(rlpInput.currentListAsBytesNoCopy(true));
    } else {
      ret = new SyncTransactionReceipt(rlpInput.readBytes());
    }
    return ret;
  }

  /**
   * Returns the state root for a state root-encoded transaction receipt
   *
   * @return the state root if the transaction receipt is state root-encoded; otherwise {@code null}
   */
  public Bytes getRlp() {
    return rlpBytes;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SyncTransactionReceipt)) {
      return false;
    }
    final SyncTransactionReceipt other = (SyncTransactionReceipt) obj;
    return rlpBytes.equals(other.rlpBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rlpBytes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("rlpBytes", rlpBytes).toString();
  }
}
