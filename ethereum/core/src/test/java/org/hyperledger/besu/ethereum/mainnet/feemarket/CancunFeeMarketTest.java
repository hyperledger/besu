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

package org.hyperledger.besu.ethereum.mainnet.feemarket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.DataGas;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CancunFeeMarketTest {

  @Test
  void dataPricePerGas() {
    CancunFeeMarket cancunFeeMarket = new CancunFeeMarket(0, Optional.empty());
    // when no excess data gas, data price per gas is 1
    assertEquals(1, cancunFeeMarket.dataPricePerGas(DataGas.ZERO).getAsBigInteger().intValue());

    // when excess data doubles, price is log
    assertEquals(
        2,
        cancunFeeMarket
            .dataPricePerGas(DataGas.fromHexString("0x180000"))
            .getAsBigInteger()
            .intValue());
    assertEquals(
        3,
        cancunFeeMarket
            .dataPricePerGas(DataGas.fromHexString("0x280000"))
            .getAsBigInteger()
            .intValue());
    assertEquals(
        5,
        cancunFeeMarket
            .dataPricePerGas(DataGas.fromHexString("0x380000"))
            .getAsBigInteger()
            .intValue());
    assertEquals(
        8,
        cancunFeeMarket
            .dataPricePerGas(DataGas.fromHexString("0x480000"))
            .getAsBigInteger()
            .intValue());
  }
}
