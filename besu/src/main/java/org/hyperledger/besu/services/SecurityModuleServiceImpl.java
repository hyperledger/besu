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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.SecurityModuleService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SecurityModuleServiceImpl implements SecurityModuleService {
  private final Map<String, SecurityModuleProvider> securityModuleProviders =
      new ConcurrentHashMap<>();

  @Override
  public void registerSecurityModule(
      final String name, final SecurityModuleProvider securityModuleProvider) {
    securityModuleProviders.put(name, securityModuleProvider);
  }

  @Override
  public Optional<SecurityModuleProvider> getByName(final String name) {
    return Optional.ofNullable(securityModuleProviders.get(name));
  }
}
