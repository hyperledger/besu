/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.permissioning.account;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.TransactionSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountPermissioningControllerFactory {

  private static final Logger LOG = LogManager.getLogger();

  public static Optional<AccountPermissioningController> create(
      final PermissioningConfiguration permissioningConfiguration,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {

    if (permissioningConfiguration == null) {
      return Optional.empty();
    }

    final Optional<AccountLocalConfigPermissioningController>
        accountLocalConfigPermissioningController =
            buildLocalConfigPermissioningController(permissioningConfiguration, metricsSystem);

    final Optional<TransactionSmartContractPermissioningController>
        transactionSmartContractPermissioningController =
            buildSmartContractPermissioningController(
                permissioningConfiguration, transactionSimulator, metricsSystem);

    if (accountLocalConfigPermissioningController.isPresent()
        || transactionSmartContractPermissioningController.isPresent()) {
      final AccountPermissioningController controller =
          new AccountPermissioningController(
              accountLocalConfigPermissioningController,
              transactionSmartContractPermissioningController);

      return Optional.of(controller);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<TransactionSmartContractPermissioningController>
      buildSmartContractPermissioningController(
          final PermissioningConfiguration permissioningConfiguration,
          final TransactionSimulator transactionSimulator,
          final MetricsSystem metricsSystem) {

    if (permissioningConfiguration.getSmartContractConfig().isPresent()) {
      final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
          permissioningConfiguration.getSmartContractConfig().get();

      if (smartContractPermissioningConfiguration.isSmartContractAccountWhitelistEnabled()) {
        final Address accountSmartContractAddress =
            smartContractPermissioningConfiguration.getAccountSmartContractAddress();

        Optional<TransactionSmartContractPermissioningController>
            transactionSmartContractPermissioningController =
                Optional.of(
                    new TransactionSmartContractPermissioningController(
                        accountSmartContractAddress, transactionSimulator, metricsSystem));
        validatePermissioningContract(transactionSmartContractPermissioningController.get());

        return transactionSmartContractPermissioningController;
      }
    }

    return Optional.empty();
  }

  private static Optional<AccountLocalConfigPermissioningController>
      buildLocalConfigPermissioningController(
          final PermissioningConfiguration permissioningConfiguration,
          final MetricsSystem metricsSystem) {

    if (permissioningConfiguration.getLocalConfig().isPresent()) {
      final LocalPermissioningConfiguration localPermissioningConfiguration =
          permissioningConfiguration.getLocalConfig().get();

      if (localPermissioningConfiguration.isAccountWhitelistEnabled()) {
        return Optional.of(
            new AccountLocalConfigPermissioningController(
                localPermissioningConfiguration, metricsSystem));
      }
    }

    return Optional.empty();
  }

  private static void validatePermissioningContract(
      final TransactionSmartContractPermissioningController
          transactionSmartContractPermissioningController) {
    try {
      LOG.debug("Validating onchain account permissioning smart contract configuration");

      final SECP256K1.Signature FAKE_SIGNATURE =
          SECP256K1.Signature.create(
              SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

      final Transaction transaction =
          Transaction.builder()
              .sender(Address.ZERO)
              .gasLimit(0)
              .gasPrice(Wei.ZERO)
              .value(Wei.ZERO)
              .payload(BytesValue.EMPTY)
              .nonce(0)
              .signature(FAKE_SIGNATURE)
              .build();

      // We don't care about the validation result. All we need it to ensure the check doesn't fail
      transactionSmartContractPermissioningController.isPermitted(transaction);
    } catch (Exception e) {
      final String msg =
          "Error validating onchain account permissioning smart contract configuration";
      LOG.error(msg + ":", e);
      throw new IllegalStateException(msg, e);
    }
  }
}
