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
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

public class ConsolidationRequestDecoder {

  public static ConsolidationRequest decode(final RLPInput rlpInput) {
    rlpInput.enterList();
    final Address sourceAddress = Address.readFrom(rlpInput);
    final BLSPublicKey sourcePublicKey = BLSPublicKey.readFrom(rlpInput);
    final BLSPublicKey targetPublicKey = BLSPublicKey.readFrom(rlpInput);
    rlpInput.leaveList();

    return new ConsolidationRequest(sourceAddress, sourcePublicKey, targetPublicKey);
  }

  public static ConsolidationRequest decodeOpaqueBytes(final Bytes input) {
    return decode(RLP.input(input));
  }
}
