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
package org.hyperledger.besu.config;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public record GenesisAccount(
    Address address,
    long nonce,
    Wei balance,
    Bytes code,
    Map<UInt256, UInt256> storage,
    Bytes32 privateKey) {}

//  static GenesisAccount fromAllocation(final GenesisAllocation allocation) {
//    return new GenesisAccount(
//        allocation.getNonce(),
//        allocation.getAddress(),
//        allocation.getBalance(),
//        allocation.getStorage(),
//        allocation.getCode());
//  }
//
//  private GenesisAccount(
//      final String hexNonce,
//      final String hexAddress,
//      final String balance,
//      final Map<String, String> storage,
//      final String hexCode) {
//    this.nonce = GenesisState.withNiceErrorMessage("nonce", hexNonce,
// GenesisState::parseUnsignedLong);
//    this.address = GenesisState.withNiceErrorMessage("address", hexAddress,
// Address::fromHexString);
//    this.balance = GenesisState.withNiceErrorMessage("balance", balance, this::parseBalance);
//    this.code = hexCode != null ? Bytes.fromHexString(hexCode) : null;
//    this.storage = parseStorage(storage);
//  }

// }
