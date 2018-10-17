/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.core;

/**
 * An interface for creating the block hash given a {@link BlockHeader}.
 *
 * <p>The algorithm to create the block hash may vary depending on the consensus mechanism used by
 * the chain.
 */
@FunctionalInterface
public interface BlockHashFunction {

  /**
   * Create the hash for a given BlockHeader.
   *
   * @param header the header to create the block hash from
   * @return a {@link Hash} containing the block hash.
   */
  Hash apply(BlockHeader header);
}
