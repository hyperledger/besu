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

import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;

public class AddressHelpers {

  /**
   * Creates a new address based on the provided src, and an integer offset. This is required for
   * managing ordered address lists.
   *
   * @param src The address from which a new address is to be derived.
   * @param offset The distance and polarity of the offset from src address.
   * @return A new address 'offset' away from the original src.
   */
  public static Address calculateAddressWithRespectTo(final Address src, final int offset) {

    // Need to crop the "0x" from the start of the hex string.
    final BigInteger inputValue = new BigInteger(src.toString().substring(2), 16);
    final BigInteger bigIntOffset = BigInteger.valueOf(offset);

    final BigInteger result = inputValue.add(bigIntOffset);

    return Address.fromHexString(result.toString(16));
  }

  public static Address ofValue(final int value) {
    return Address.fromHexString(String.format("%020x", value));
  }
}
