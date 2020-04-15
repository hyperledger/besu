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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.nodekey.SecurityModule;

import java.util.Optional;
import java.util.function.Function;

/**
 * This service allows plugins to register Security Module which is abstraction of
 * cryptographic operations by deferring to specific provider such as BouncyCastle
 */
@Unstable
public interface SecurityModuleService {

  /**
   * Registers a factory as available for creating SecurityProvider instances.
   *
   * @param name The name to identify the Security Provider Supplier Function
   * @param securityProviderSupplier Register reference of function that can apply BesuConfiguration
   *     to provide SecurityModule implementation.
   */
  void registerSecurityModule(
      final String name,
      final Function<BesuConfiguration, SecurityModule> securityProviderSupplier);

  /**
   * Retrieves a registered Security Module Function corresponding to the specified name
   *
   * @param name The name associated with supplier function to retrieve
   * @return Optional reference of Function that can provide a Security Provider implementation, or
   *     empty if it hasn't been registered.
   */
  Optional<Function<BesuConfiguration, SecurityModule>> getByName(String name);
}
