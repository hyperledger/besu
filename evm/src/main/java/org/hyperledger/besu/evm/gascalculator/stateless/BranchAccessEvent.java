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

final class BranchAccessEvent extends AccessEvent<Address> {

  public BranchAccessEvent(final Address key, final UInt256 index) {
    super(key, index);
  }

  @Override
  public AccessEvent<?> getBranchEvent() {
    return this;
  }

  @Override
  public String toShortString() {
    return String.format("{addr=%s,treeIndex=%s}", key, getIndex().toShortHexString());
  }

  @Override
  public String toString() {
    return String.format(
        "BranchAccessEvent { key=%s, index=%s }", key, getIndex().toShortHexString());
  }

  @Override
  public String costSchedulePrettyPrint() {
    String message = "";
    if (getBranchEvent().isBranchRead()) {
      message += "\n\tWITNESS_BRANCH_COST " + getBranchReadCost();
    }
    if (getBranchEvent().isBranchWrite()) {
      message += "\n\tSUBTREE_EDIT_COST " + getBranchWriteCost();
    }
    return message;
  }
}
