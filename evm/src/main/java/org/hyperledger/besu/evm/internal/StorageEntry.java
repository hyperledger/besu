/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.internal;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Storage entry. */
public class StorageEntry {
  private final UInt256 offset;
  private final Bytes value;

  /**
   * Instantiates a new Storage entry.
   *
   * @param offset the offset
   * @param value the value
   */
  public StorageEntry(final UInt256 offset, final Bytes value) {
    this.offset = offset;
    this.value = value;
  }

  /**
   * Gets offset.
   *
   * @return the offset
   */
  public UInt256 getOffset() {
    return offset;
  }

  /**
   * Gets value.
   *
   * @return the value
   */
  public Bytes getValue() {
    return value;
  }
}
