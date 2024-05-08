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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.Withdrawal;

/** The type Withdrawal adapter. */
public class WithdrawalAdapter {

  /** The Withdrawal. */
  Withdrawal withdrawal;

  /**
   * Instantiates a new Withdrawal adapter.
   *
   * @param withdrawal the withdrawal
   */
  public WithdrawalAdapter(final Withdrawal withdrawal) {
    this.withdrawal = withdrawal;
  }

  /**
   * Gets index.
   *
   * @return the index
   */
  public Long getIndex() {
    return withdrawal.getIndex().toLong();
  }

  /**
   * Gets validator.
   *
   * @return the validator
   */
  public Long getValidator() {
    return withdrawal.getValidatorIndex().toLong();
  }

  /**
   * Gets address.
   *
   * @return the address
   */
  public Address getAddress() {
    return withdrawal.getAddress();
  }

  /**
   * Gets amount.
   *
   * @return the amount
   */
  public Long getAmount() {
    return withdrawal.getAmount().getAsBigInteger().longValue();
  }
}
