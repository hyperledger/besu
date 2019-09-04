/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin.data;

import tech.pegasys.pantheon.plugin.Unstable;

import java.math.BigInteger;
import java.util.Optional;

/**
 * A transaction is a single cryptographically-signed instruction constructed by an actor externally
 * to the scope of Ethereum. While it is assumed that the ultimate external actor will be human in
 * nature, software tools will be used in its construction and dissemination.
 *
 * <p>There are two types of transactions: those which result in message calls and those which
 * result in the creation of new accounts with associated code (known informally as ‘contract
 * creation’). Message call transactions will have an address present in the {@link #getTo} method
 * whereas contract creation transactions will not.
 */
@Unstable
public interface Transaction {

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
   * @return the maximum amount of gas that should be used in executing this * transaction.
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
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
   */
  BigInteger getR();

  /**
   * Value corresponding to the 'V' component of the signature of the transaction.
   *
   * @return the 'V' component of the signature
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
   * An unlimited size byte array specifying the EVM-code for the account // initialisation
   * procedure.
   *
   * <p>Only present if this is a contract creation transaction, which is only true if {@link
   * #getTo} is empty.
   *
   * @return if present, the contract init code.
   */
  Optional<? extends UnformattedData> getInit();

  /**
   * An unlimited size byte array specifying theinput data of the message call.
   *
   * <p>Only present if this is a message call transaction, which is only true if {@link #getTo} is
   * present.
   *
   * @return if present, the message call data
   */
  Optional<? extends UnformattedData> getData();
}
