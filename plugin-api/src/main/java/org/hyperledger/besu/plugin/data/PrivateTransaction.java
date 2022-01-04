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
package org.hyperledger.besu.plugin.data;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface PrivateTransaction {
  /**
   * A scalar value equal to the number of transactions sent by the sender.
   *
   * @return the number of transactions sent by the sender.
   */
  long getNonce();

  /**
   * A scalar value equal to the number of Wei to be paid per unit of gas for all computation costs
   * incurred as a result of the execution of this transaction.
   *
   * @return the quantity of Wei per gas unit paid.
   */
  Quantity getGasPrice();

  /**
   * A scalar value equal to the maximum amount of gas that should be used in executing this
   * transaction. This is paid up-front, before any computation is done and may not be increased
   * later.
   *
   * @return the maximum amount of gas that should be used in executing this transaction.
   */
  long getGasLimit();

  /**
   * The 160-bit address of the message call’s recipient. For a contract creation transaction this
   * address will not be present.
   *
   * @return address of the recipient
   */
  Optional<? extends Address> getTo();

  /**
   * A scalar value equal to the number of Wei to be transferred to the message call’s recipient or,
   * in the case of contract creation, as an endowment to the newly created account
   *
   * @return value equal to the number of Wei to be transferred
   */
  Quantity getValue();

  /**
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
   */
  BigInteger getV();

  /**
   * Value corresponding to the 'R' component of the signature of the transaction.
   *
   * @return the 'R' component of the signature
   */
  BigInteger getR();

  /**
   * Value corresponding to the 'S' component of the signature of the transaction.
   *
   * @return the 'S' component of the signature
   */
  BigInteger getS();

  /**
   * The 160-bit address of the account sending the transaction, extracted from the v, r, s
   * parameters.
   *
   * @return The address of the account that sent this transaction.
   */
  Address getSender();

  /**
   * The chainId, computed from the 'V' portion of the signature. Used for replay protection. If
   * replay protection is not enabled this value will not be present.
   *
   * @return The chainId for transaction.
   */
  Optional<BigInteger> getChainId();

  /**
   * The data payload of this private transaction. e.g compiled smart contract code or encoded
   * method data.
   *
   * @return the transaction payload
   */
  Bytes getPayload();

  /**
   * The public key of the sender of this private transaction.
   *
   * @return public key of the sender
   */
  Bytes getPrivateFrom();

  /**
   * The public key of the recipients of this private transaction.
   *
   * <p>Only present if no privacy group {@link #getPrivacyGroupId} is present.
   *
   * @return public keys of the recipients
   */
  Optional<List<Bytes>> getPrivateFor();

  /**
   * The identifier for the group of participants.
   *
   * <p>Only present if no private for {@link #getPrivateFor} is present.
   *
   * @return public keys of the recipients
   */
  Optional<Bytes> getPrivacyGroupId();

  /**
   * The type of privacy restriction. If restricted, the private transaction is only distributed to
   * members. If unrestricted, the transaction is distributed to everyone but only decipherable by
   * members.
   *
   * @return privacy restriction unrestricted/restricted
   */
  Restriction getRestriction();
}
