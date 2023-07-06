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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Quantity;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The interface Privacy genesis account. */
public interface PrivacyGenesisAccount {
  /**
   * The address of the initial genesis allocation/pre-compile
   *
   * @return Ethereum account address
   */
  Address getAddress();

  /**
   * Any storage that the address requires
   *
   * @return account storage
   */
  Map<UInt256, UInt256> getStorage();

  /**
   * The initial nonce assigned to the account.
   *
   * @return the nonce
   */
  Long getNonce();

  /**
   * The initial balance assigned to the account.
   *
   * @return the balance
   */
  Quantity getBalance();

  /**
   * The initial code for the account.
   *
   * <p>Note! this must be the contract code and not the code to deploy the contract.
   *
   * @return the wallet code. Can be null or 0x
   */
  Bytes getCode();
}
