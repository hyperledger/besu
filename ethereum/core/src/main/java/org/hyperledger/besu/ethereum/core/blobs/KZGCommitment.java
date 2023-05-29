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
package org.hyperledger.besu.ethereum.core.blobs;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

public class KZGCommitment {
  final Bytes data;

  public KZGCommitment(final Bytes data) {
    this.data = data;
  }

  public static KZGCommitment readFrom(final RLPInput input) {
    final Bytes bytes = input.readBytes();
    return new KZGCommitment(bytes);
  }

  public void writeTo(final RLPOutput out) {
    out.writeBytes(data);
  }

  public Bytes getData() {
    return data;
  }
}
