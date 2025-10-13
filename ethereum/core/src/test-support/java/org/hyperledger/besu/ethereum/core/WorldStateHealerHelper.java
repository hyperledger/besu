/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive.WorldStateHealer;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class WorldStateHealerHelper {

  public static WorldStateHealer throwingHealer(
      final Optional<Address> maybeAccountToRepair, final Bytes location) {
    throw new RuntimeException(
        "World state needs to be healed: "
            + maybeAccountToRepair.map(address -> "account to repair: " + address).orElse("")
            + " location: "
            + location.toHexString());
  }

  public static Supplier<WorldStateHealer> throwingWorldStateHealerSupplier() {
    return () -> WorldStateHealerHelper::throwingHealer;
  }
}
