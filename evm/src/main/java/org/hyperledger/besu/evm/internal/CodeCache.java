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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.util.cache.MemoryBoundCache;

/** The Code cache. */
public class CodeCache {

  private final MemoryBoundCache<Hash, Code> cache;

  /** Instantiates a new Code cache. */
  public CodeCache() {
    this.cache = new MemoryBoundCache<>(256 * 1024 * 1024, CodeMemoryFootprint::estimate);
  }

  /**
   * Gets if present.
   *
   * @param codeHash the code hash
   * @return the code if present
   */
  public Code getIfPresent(final Hash codeHash) {
    return cache.getIfPresent(codeHash);
  }

  /**
   * Put.
   *
   * @param codeHash the code hash
   * @param code the code
   */
  public void put(final Hash codeHash, final Code code) {
    cache.put(codeHash, code);
  }
}
