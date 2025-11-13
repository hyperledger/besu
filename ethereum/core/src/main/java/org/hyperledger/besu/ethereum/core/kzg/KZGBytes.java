/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.kzg;

import org.hyperledger.besu.util.HexUtils;

import org.apache.tuweni.bytes.Bytes;

public class KZGBytes<T extends Bytes> {
  private final String cachedHex;
  private final T bytes;

  public KZGBytes(final T bytes) {
    this.cachedHex = HexUtils.toFastHex(bytes, true);
    this.bytes = bytes;
  }

  /** Returns the cached hex string. */
  public String toHexString() {
    return cachedHex;
  }

  public T getData() {
    return bytes;
  }
}
