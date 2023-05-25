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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.Withdrawal;

public class WithdrawalAdapter {

  Withdrawal withdrawal;

  public WithdrawalAdapter(final Withdrawal withdrawal) {
    this.withdrawal = withdrawal;
  }

  public Long getIndex() {
    return withdrawal.getIndex().toLong();
  }

  public Long getValidator() {
    return withdrawal.getValidatorIndex().toLong();
  }

  public Address getAddress() {
    return withdrawal.getAddress();
  }

  public Long getAmount() {
    return withdrawal.getAmount().getAsBigInteger().longValue();
  }
}
