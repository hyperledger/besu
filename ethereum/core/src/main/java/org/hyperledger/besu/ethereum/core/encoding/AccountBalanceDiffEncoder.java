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

package org.hyperledger.besu.ethereum.core.encoding;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountBalanceDiff;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class AccountBalanceDiffEncoder {

  public static void encode(final AccountBalanceDiff accountBalanceDiff, final RLPOutput out) {
    out.startList();
    out.writeBytes(accountBalanceDiff.getAddress());
    out.writeList(accountBalanceDiff.getBalanceChanges(), (balanceChange, balanceChangeOut) -> {
      balanceChangeOut.startList();
      out.writeInt(balanceChange.getTxIndex());
      out.writeBytes(Bytes.of(balanceChange.getDelta().toByteArray()));
      balanceChangeOut.endList();
    });
    out.endList();
  }
}
