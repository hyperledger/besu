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
package org.hyperledger.besu.util.bytes;

/** Base abstract implementation for {@link Bytes32Backed} implementations. */
public class AbstractBytes32Backed implements Bytes32Backed {
  protected final Bytes32 bytes;

  protected AbstractBytes32Backed(final Bytes32 bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes32 getBytes() {
    return bytes;
  }

  @Override
  public int size() {
    return bytes.size();
  }
}
