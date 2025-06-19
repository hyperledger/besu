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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountCodeDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

public class AccountCodeDiffDecoder {

  private AccountCodeDiffDecoder() {
    // private constructor
  }

  public static AccountCodeDiff decode(final RLPInput in) {
    final RLPInput outer = in.readAsRlp();
    outer.enterList();
    final Address address = Address.readFrom(outer);

    final RLPInput codeChangeInput = outer.readAsRlp();
    codeChangeInput.enterList();
    final int txIndex = codeChangeInput.readInt();
    final Bytes newCode = codeChangeInput.readBytes();
    codeChangeInput.leaveList();

    outer.leaveList();
    return new AccountCodeDiff(address, new CodeChange(txIndex, newCode));
  }
}
