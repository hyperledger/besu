/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.plugin.data;

import org.hyperledger.besu.datatypes.BLSSignature;
import org.hyperledger.besu.datatypes.PublicKey;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.plugin.Unstable;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

/**
 * A deposit is a system-level operation to support validator deposits that are pushed from the EVM
 * to beacon chain.
 */
@Unstable
public interface Deposit {

  /**
   * Public key of the address that sends the deposit
   *
   * @return public key of sender
   */
  PublicKey getPubkey();

  /**
   * Withdrawal credential that contains info that will be used for verifying the destination of
   * valid withdrawals
   *
   * @return withdrawal credential
   */
  Bytes32 getWithdrawalCredentials();

  /**
   * Amount of ether to be sent to the deposit contract
   *
   * @return deposit ether amount
   */
  Quantity getAmount();

  /**
   * Signature that will be used together with the public key to verify the validity of this Deposit
   *
   * @return signature
   */
  BLSSignature getSignature();

  /**
   * A monotonically increasing index, starting from 0 that increments by 1 per deposit to uniquely
   * identify each deposit
   *
   * @return deposit index
   */
  UInt64 getIndex();
}
