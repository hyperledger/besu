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

import java.util.List;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.PerTxAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotAccess;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

public class AccountAccessDecoder {

  private AccountAccessDecoder() {
    // private constructor
  }

  public static AccountAccess decode(final RLPInput in) {
    RLPInput transactionRlp = in.readAsRlp();
    transactionRlp.enterList();
    Address address = Address.readFrom(in);
    List<SlotAccess> slotAccesses = in.readList((slotAccessIn) -> {
      slotAccessIn.enterList();
      StorageSlotKey slotKey = new StorageSlotKey(slotAccessIn.readUInt256Scalar());
      List<PerTxAccess> accesses = slotAccessIn.readList((perTxAccessIn) -> {
        int txIndex = perTxAccessIn.readInt();
        Bytes valueAfter = perTxAccessIn.readBytes();
        return new PerTxAccess(txIndex, valueAfter);
      });
      slotAccessIn.leaveList();
      return new SlotAccess(slotKey, accesses);
    });
    transactionRlp.leaveList();
    return new AccountAccess(address, slotAccesses);
  }
}
