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
package org.hyperledger.besu.ethereum.permissioning.account;

import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.TransactionPermissioningProvider;

import java.util.List;
import java.util.Optional;

public class AccountPermissioningControllerFactory {

  public static Optional<AccountPermissioningController> create(
      final PermissioningConfiguration permissioningConfiguration,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem,
      final List<TransactionPermissioningProvider> pluginProviders) {

    if (permissioningConfiguration == null) {
      return Optional.empty();
    }

    final Optional<AccountLocalConfigPermissioningController>
        accountLocalConfigPermissioningController =
            buildLocalConfigPermissioningController(permissioningConfiguration, metricsSystem);

    if (accountLocalConfigPermissioningController.isPresent() || pluginProviders.size() > 0) {

      final AccountPermissioningController controller =
          new AccountPermissioningController(
              accountLocalConfigPermissioningController, pluginProviders);

      return Optional.of(controller);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<AccountLocalConfigPermissioningController>
      buildLocalConfigPermissioningController(
          final PermissioningConfiguration permissioningConfiguration,
          final MetricsSystem metricsSystem) {

    if (permissioningConfiguration.getLocalConfig().isPresent()) {
      final LocalPermissioningConfiguration localPermissioningConfiguration =
          permissioningConfiguration.getLocalConfig().get();

      if (localPermissioningConfiguration.isAccountAllowlistEnabled()) {
        return Optional.of(
            new AccountLocalConfigPermissioningController(
                localPermissioningConfiguration, metricsSystem));
      }
    }

    return Optional.empty();
  }
}
