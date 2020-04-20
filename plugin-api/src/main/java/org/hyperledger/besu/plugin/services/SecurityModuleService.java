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
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleProvider;

import java.util.Optional;

/**
 * This service allows plugins to register a Security Module, which is abstraction of cryptographic
 * operations that defer to specific provider (e.g. BouncyCastle).
 */
@Unstable
public interface SecurityModuleService {

  /**
   * Registers a provider of security modules.
   *
   * @param name The name to identify the Security Provider Supplier Function
   * @param securityModuleProvider Register reference of SecurityModuleProvider.
   */
  void registerSecurityModule(
      final String name, final SecurityModuleProvider securityModuleProvider);

  /**
   * Retrieves a registered Security Module Provider corresponding to the specified name
   *
   * @param name The name associated with Security Module Provider
   * @return Optional reference Security Module Provider, or empty if it hasn't been registered.
   */
  Optional<SecurityModuleProvider> getByName(String name);
}
