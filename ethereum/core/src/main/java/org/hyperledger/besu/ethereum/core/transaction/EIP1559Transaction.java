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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

public class EIP1559Transaction extends org.hyperledger.besu.plugin.data.EIP1559Transaction {

  public EIP1559Transaction(
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId) {
    super(
        nonce,
        gasPrice,
        gasPremium,
        feeCap,
        gasLimit,
        to,
        value,
        signature,
        payload,
        sender,
        chainId);
  }

  public EIP1559Transaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId) {
    super(nonce, gasPrice, gasLimit, to, value, signature, payload, sender, chainId);
  }

  /**
   * Return the transaction gas premium.
   *
   * @return the transaction gas premium
   */
  public Optional<Quantity> getGasPremium() {
    return super.getGasPremium();
  }

  /**
   * Return the transaction fee cap.
   *
   * @return the transaction fee cap
   */
  @Override
  public Optional<Quantity> getFeeCap() {
    return super.getFeeCap();
  }

  @Override
  public TransactionType getType() {
    return TransactionType.EIP1559;
  }
}
