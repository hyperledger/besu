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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.datatypes.Address;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Encapsulates a group of {@link PrecompiledContract}s used together. */
public class PrecompileContractRegistry {

  private final Map<Address, PrecompiledContract> precompiles;

  /** Instantiates a new Precompile contract registry. */
  public PrecompileContractRegistry() {
    this.precompiles = new HashMap<>(16);
  }

  /**
   * Get precompiled contract.
   *
   * @param address the address
   * @return the precompiled contract
   */
  public PrecompiledContract get(final Address address) {
    return precompiles.get(address);
  }

  /**
   * Put.
   *
   * @param address the address
   * @param precompile the precompile
   */
  public void put(final Address address, final PrecompiledContract precompile) {
    precompiles.put(address, precompile);
  }

  /**
   * Gets the addresses of the precompiled contracts.
   *
   * @return the addresses
   */
  public Set<Address> getPrecompileAddresses() {
    return Collections.unmodifiableSet(precompiles.keySet());
  }
}
