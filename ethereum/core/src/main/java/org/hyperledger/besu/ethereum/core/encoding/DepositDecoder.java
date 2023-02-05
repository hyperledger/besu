/*
 * Copyright Hyperledger Besu Contributors.
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
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class DepositDecoder {

  public static Deposit decode(final RLPInput rlpInput) {
    rlpInput.enterList();
    final Object pubKey = null; //TODO
    final Object withdrawalCredential = null; // TODO
    final GWei amount = GWei.of(rlpInput.readUInt64Scalar());
    final SECPSignature signature = null; // TODO
    final UInt64 index = UInt64.valueOf(rlpInput.readBigIntegerScalar());
    rlpInput.leaveList();

    return new Deposit(pubKey, withdrawalCredential, amount, signature, index);
  }

  public static Deposit decodeOpaqueBytes(final Bytes input) {
    return decode(RLP.input(input));
  }
}
