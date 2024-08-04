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

/**
 * The WithdrawalAdapter class provides methods to retrieve the withdrawal details.
 *
 * <p>This class is used to adapt a Withdrawal object into a format that can be used by GraphQL. The
 * Withdrawal object is provided at construction time.
 *
 * <p>The class provides methods to retrieve the index, validator, address, and amount of the
 * withdrawal.
 */
public class WithdrawalAdapter {

  Withdrawal withdrawal;

  /**
   * Constructs a new WithdrawalAdapter object.
   *
   * @param withdrawal the Withdrawal object to adapt.
   */
  public WithdrawalAdapter(final Withdrawal withdrawal) {
    this.withdrawal = withdrawal;
  }

  /**
   * Returns the index of the withdrawal.
   *
   * @return the index of the withdrawal.
   */
  public Long getIndex() {
    return withdrawal.getIndex().toLong();
  }

  /**
   * Returns the validator of the withdrawal.
   *
   * @return the validator of the withdrawal.
   */
  public Long getValidator() {
    return withdrawal.getValidatorIndex().toLong();
  }

  /**
   * Returns the address of the withdrawal.
   *
   * @return the address of the withdrawal.
   */
  public Address getAddress() {
    return withdrawal.getAddress();
  }

  /**
   * Returns the amount of the withdrawal.
   *
   * @return the amount of the withdrawal.
   */
  public Long getAmount() {
    return withdrawal.getAmount().getAsBigInteger().longValue();
  }
}
