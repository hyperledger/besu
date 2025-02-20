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
package org.hyperledger.besu.datatypes;

import org.apache.tuweni.units.bigints.UInt256;

/**
 * The access event key corresponding to a leaf access in the stateless trie.
 *
 * @param branchAccessKey key to the stateless trie of the branch being accessed.
 * @param leafIndex stateless trie index of the leaf being accessed.
 */
public record LeafAccessKey(BranchAccessKey branchAccessKey, UInt256 leafIndex) {

  /**
   * Prints a {@code toString()} version suitable printed in JSON format.
   *
   * @return the string
   */
  public String toJsonObject() {
    return String.format(
        "{\"addr\": \"%s\",\"treeIndex\": \"%s\",\"subIndex\": \"%s\"}",
        branchAccessKey.address(),
        branchAccessKey.treeIndex().toShortHexString(),
        leafIndex.toShortHexString());
  }
}
