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

final class LeafAccessEvent extends AccessEvent<AccessEvent<Address>> {
  public LeafAccessEvent(final BranchAccessEvent branchEvent, final UInt256 subIndex) {
    super(branchEvent, subIndex);
  }

  @Override
  public AccessEvent<?> getBranchEvent() {
    return key;
  }

  @Override
  public String toShortString() {
    return String.format("{%s,subIndex=%s}", key.toShortString(), getIndex().toShortHexString());
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
}
