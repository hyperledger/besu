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

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Objects;

public class RLPMapEntry {
  private final BytesValue key;
  private final BytesValue value;

  public RLPMapEntry(final BytesValue key, final BytesValue value) {
    this.key = key;
    this.value = value;
  }

  public BytesValue getKey() {
    return key;
  }

  public BytesValue getValue() {
    return value;
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytesValue(key);
    out.writeBytesValue(value);

    out.endList();
  }

  public static RLPMapEntry readFrom(final RLPInput input) {
    input.enterList();

    final RLPMapEntry rlpMapEntry = new RLPMapEntry(input.readBytesValue(), input.readBytesValue());

    input.leaveList();
    return rlpMapEntry;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final RLPMapEntry rlpMapEntry = (RLPMapEntry) o;
    return key.equals(rlpMapEntry.key) && value.equals(rlpMapEntry.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }
}
