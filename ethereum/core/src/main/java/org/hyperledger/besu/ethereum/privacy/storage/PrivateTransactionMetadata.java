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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

/** Mined private transaction metadata. */
public class PrivateTransactionMetadata {
  private final Hash privacyMarkerTransactionHash;
  private final BytesValue privacyGroupId;
  private final Hash stateRoot;

  public PrivateTransactionMetadata(
      final Hash privacyMarkerTransactionHash,
      final BytesValue privacyGroupId,
      final Hash stateRoot) {
    this.privacyMarkerTransactionHash = privacyMarkerTransactionHash;
    this.privacyGroupId = privacyGroupId;
    this.stateRoot = stateRoot;
  }

  public Hash getStateRoot() {
    return stateRoot;
  }

  public Hash getPrivacyMarkerTransactionHash() {
    return privacyMarkerTransactionHash;
  }

  public BytesValue getPrivacyGroupId() {
    return privacyGroupId;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytesValue(privacyMarkerTransactionHash);
    out.writeBytesValue(privacyGroupId);
    out.writeBytesValue(stateRoot);

    out.endList();
  }

  public static PrivateTransactionMetadata readFrom(final RLPInput input) {
    input.enterList();

    final PrivateTransactionMetadata privateTransactionMetadata =
        new PrivateTransactionMetadata(
            Hash.wrap(input.readBytes32()), input.readBytesValue(), Hash.wrap(input.readBytes32()));

    input.leaveList();
    return privateTransactionMetadata;
  }
}
