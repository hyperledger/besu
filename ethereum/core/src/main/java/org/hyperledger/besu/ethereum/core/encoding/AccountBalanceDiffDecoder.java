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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountBalanceDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class AccountBalanceDiffDecoder {

  private AccountBalanceDiffDecoder() {
    // private constructor
  }

  public static AccountBalanceDiff decode(final RLPInput in) {
    RLPInput accountBalanceRlp = in.readAsRlp();
    accountBalanceRlp.enterList();
    Address address = Address.readFrom(in);
    List<BalanceChange> changes = in.readList((changeIn) -> {
      changeIn.enterList();
      int txIndex = changeIn.readInt();
      Bytes delta = changeIn.readBytes();
      changeIn.leaveList();
      return new BalanceChange(txIndex, delta.toBigInteger());
    });
    return new AccountBalanceDiff(address, changes);
  }
}
