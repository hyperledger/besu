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

import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountAccess;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

// TODO: Doublecheck writing to the correct output
public class AccountAccessEncoder {

  public static void encode(final AccountAccess accountAccess, final RLPOutput out) {
    out.startList();
    out.writeBytes(accountAccess.getAddress());
    out.writeList(
        accountAccess.getSlotAccesses(),
        (slotAccess, slotAccessOut) -> {
          slotAccessOut.startList();
          out.writeUInt256Scalar(slotAccess.getSlot().getSlotKey().get());
          out.writeList(
              slotAccess.getPerTxAccesses(),
              (perTxAccess, perTxAccessOut) -> {
                perTxAccessOut.writeInt(perTxAccess.getTxIndex());
                if (perTxAccess.getValueAfter().isPresent()) {
                  perTxAccessOut.writeBytes(perTxAccess.getValueAfter().get());
                } else {
                  perTxAccessOut.writeNull();
                }
              });
          slotAccessOut.endList();
        });
    out.endList();
  }
}
