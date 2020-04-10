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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSITIONAL_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FRONTIER_TRANSACTIONS;

import org.junit.Test;

public class AcceptedTransactionTypesTest {

  @Test
  public void givenFrontier_isFrontierAcceptedReturnsTrue() {
    assertThat(FRONTIER_TRANSACTIONS.isFrontierAccepted()).isTrue();
  }

  @Test
  public void givenFrontier_isEIP1559AcceptedReturnsFalse() {
    assertThat(FRONTIER_TRANSACTIONS.isEIP1559Accepted()).isFalse();
  }

  @Test
  public void givenTransitional_isFrontierAcceptedReturnsTrue() {
    assertThat(FEE_MARKET_TRANSITIONAL_TRANSACTIONS.isFrontierAccepted()).isTrue();
  }

  @Test
  public void givenTransitional_isEIP1559AcceptedReturnsTrue() {
    assertThat(FEE_MARKET_TRANSITIONAL_TRANSACTIONS.isEIP1559Accepted()).isTrue();
  }

  @Test
  public void givenEIP1559_isFrontierAcceptedReturnsFalse() {
    assertThat(FEE_MARKET_TRANSACTIONS.isFrontierAccepted()).isFalse();
  }

  @Test
  public void givenFrontier_isEIP1559AcceptedReturnsTrue() {
    assertThat(FEE_MARKET_TRANSACTIONS.isEIP1559Accepted()).isTrue();
  }

  @Test
  public void isEIP1559Accepted() {}

  @Test
  public void testFrontierToString() {
    assertThat(FRONTIER_TRANSACTIONS.toString()).isEqualTo("frontier: true / eip1559: false");
  }

  @Test
  public void testTransitionalToString() {
    assertThat(FEE_MARKET_TRANSITIONAL_TRANSACTIONS.toString())
        .isEqualTo("frontier: true / eip1559: true");
  }

  @Test
  public void testEIP1559OnlyToString() {
    assertThat(FEE_MARKET_TRANSACTIONS.toString()).isEqualTo("frontier: false / eip1559: true");
  }
}
