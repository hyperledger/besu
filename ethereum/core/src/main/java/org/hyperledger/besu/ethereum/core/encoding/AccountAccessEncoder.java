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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

// TODO: Doublecheck writing to the correct output
public class AccountAccessEncoder {

  public static void encode(final AccountAccess accountAccess, final RLPOutput out) {
    out.startList();
    out.writeBytes(accountAccess.getAddress());
    out.writeList(
        accountAccess.getSlotAccesses(),
        (slotAccess, slotAccessOut) -> {
          slotAccessOut.startList();
          out.writeUInt256Scalar(slotAccess.getSlot().getSlotKey().orElse(UInt256.ZERO));
          out.writeList(
              slotAccess.getPerTxAccesses(),
              (perTxAccess, perTxAccessOut) -> {
                // TODO: Really not sure about the null value cases
                perTxAccessOut.writeInt(perTxAccess.getTxIndex().orElse(0));
                perTxAccessOut.writeBytes(perTxAccess.getValueAfter().orElse(Bytes.EMPTY));
              });
          slotAccessOut.endList();
        });
    out.endList();
  }
}
