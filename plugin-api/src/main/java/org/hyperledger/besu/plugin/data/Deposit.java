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
package org.hyperledger.besu.plugin.data;

import org.apache.tuweni.units.bigints.UInt64;

/**
 * A deposit is a system-level operation to support validator deposits that are pushed from
 * the beacon chain to EVM.
 */
public interface Deposit {

  PublicKey getPublicKey();

  WithdrawalCredential getWithdrawalCredentials();

  /**
   * Amount of ether to be withdrawn that be credit to the recipient address
   *
   * @return withdrawn ether amount
   */
  Quantity getAmount();

  Signature getSignature();

  /**
   * A monotonically increasing index, starting from 0 that increments by 1 per withdrawal to
   * uniquely identify each withdrawal
   *
   * @return deposit index
   */
  UInt64 getIndex();

}
