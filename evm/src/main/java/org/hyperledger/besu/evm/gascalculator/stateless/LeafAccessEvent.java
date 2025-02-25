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
package org.hyperledger.besu.evm.gascalculator.stateless;

import org.hyperledger.besu.datatypes.AccessEvent;
import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.units.bigints.UInt256;

/** The access event key corresponding to a leaf access in the stateless trie. */
public final class LeafAccessEvent extends AccessEvent<AccessEvent<Address>> {

  /**
   * The constructor.
   *
   * @param branchEvent branchEvent to be used as key to the stateless trie of the leaf being
   *     accessed.
   * @param subIndex stateless trie index of the leaf being accessed.
   */
  public LeafAccessEvent(final BranchAccessEvent branchEvent, final UInt256 subIndex) {
    super(branchEvent, subIndex);
  }

  @Override
  public AccessEvent<?> getBranchEvent() {
    return key;
  }

  @Override
  public String toString() {
    return String.format("LeafAccessEvent { %s, subIndex=%s }", key, getIndex().toShortHexString());
  }

  @Override
  public String costSchedulePrettyPrint() {
    String message = getBranchEvent().costSchedulePrettyPrint();
    if (isLeafRead()) {
      message += "\n\tWITNESS_CHUNK_COST " + getLeafReadCost();
    }
    if (isLeafReset()) {
      message += "\n\tCHUNK_EDIT_COST " + getLeafResetCost();
    }
    if (isLeafSet()) {
      message += "\n\tCHUNK_FILL_COST " + getLeafSetCost();
    }
    return message;
  }

  @Override
  public String toJsonObject() {
    return String.format(
        "{\"addr\": \"%s\",\"treeIndex\": \"%s\",\"subIndex\": \"%s\"}",
        getBranchEvent().getKey(),
        getBranchEvent().getIndex().toShortHexString(),
        getIndex().toShortHexString());
  }
}
