/*
 * Copyright ConsenSys AG.
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

package org.hyperledger.besu.ethereum.transaction;

import static org.hyperledger.besu.ethereum.core.Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX;
import static org.hyperledger.besu.ethereum.core.Transaction.GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN;

import java.math.BigInteger;

public class GoQuorumPrivateTransactionDetector {
  /**
   * Returns whether or not <i>v</i> indicates a GoQuorum private transaction.
   *
   * @param v the v value of a transaction
   * @return true if GoQuorum private transaction, false otherwise
   */
  public static final boolean isGoQuorumPrivateTransactionV(final BigInteger v) {
    return ((v.compareTo(GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX) <= 0)
        && (v.compareTo(GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN)) >= 0);
  }
}
