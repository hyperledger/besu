/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.transaction;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.plugin.data.ChainIdTransaction;

import java.math.BigInteger;

public interface ECDSASignedAndReplayProtectedTransaction
    extends org.hyperledger.besu.plugin.data.ECDSASignedTransaction, ChainIdTransaction {

  // Used for transactions that are not tied to a specific chain
  // (e.g. does not have a chain id associated with it).
  BigInteger REPLAY_UNPROTECTED_V_BASE = BigInteger.valueOf(27);
  BigInteger REPLAY_PROTECTED_V_BASE = BigInteger.valueOf(35);
  BigInteger TWO = BigInteger.valueOf(2);
  /**
   * Returns the signature used to sign the transaction.
   *
   * @return the signature used to sign the transaction
   */
  SECP256K1.Signature getSignature();

  @Override
  default BigInteger getS() {
    return getSignature().getS();
  }

  @Override
  default BigInteger getR() {
    return getSignature().getR();
  }

  @Override
  default BigInteger getV() {
    final BigInteger v;
    final BigInteger recId = BigInteger.valueOf(getSignature().getRecId());
    if (getChainId().isEmpty()) {
      v = recId.add(REPLAY_UNPROTECTED_V_BASE);
    } else {
      v = recId.add(REPLAY_PROTECTED_V_BASE).add(TWO.multiply(getChainId().get()));
    }
    return v;
  }
}
