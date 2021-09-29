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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.contract;

import java.util.Arrays;
import java.util.List;

import org.web3j.tx.Contract;
import org.web3j.utils.Restriction;

public class PrivateContractTransactions {

  public <T extends Contract>
      DeployPrivateSmartContractWithPrivacyGroupIdTransaction<T>
          createSmartContractWithPrivacyGroupId(
              final Class<T> clazz,
              final String transactionSigningKey,
              final Restriction restriction,
              final String privateFrom,
              final String privacyGroupId) {
    return new DeployPrivateSmartContractWithPrivacyGroupIdTransaction<>(
        clazz, transactionSigningKey, restriction, privateFrom, privacyGroupId);
  }

  public <T extends Contract>
      DeployPrivateSmartContractWithPrivacyGroupIdTransaction<T>
          createSmartContractWithPrivacyGroupId(
              final Class<T> clazz,
              final String transactionSigningKey,
              final String privateFrom,
              final String privacyGroupId) {
    return new DeployPrivateSmartContractWithPrivacyGroupIdTransaction<>(
        clazz, transactionSigningKey, Restriction.RESTRICTED, privateFrom, privacyGroupId);
  }

  public <T extends Contract> DeployPrivateSmartContractTransaction<T> createSmartContract(
      final Class<T> clazz,
      final String transactionSigningKey,
      final String privateFrom,
      final String... privateFor) {
    return createSmartContract(
        clazz, transactionSigningKey, privateFrom, Arrays.asList(privateFor));
  }

  public <T extends Contract> DeployPrivateSmartContractTransaction<T> createSmartContract(
      final Class<T> clazz,
      final String transactionSigningKey,
      final String privateFrom,
      final List<String> privateFor) {
    return new DeployPrivateSmartContractTransaction<>(
        clazz, transactionSigningKey, privateFrom, privateFor);
  }

  public CallPrivateSmartContractFunction callSmartContract(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final Restriction restriction,
      final String privateFrom,
      final String... privateFor) {
    return callSmartContract(
        contractAddress,
        encodedFunction,
        transactionSigningKey,
        restriction,
        privateFrom,
        Arrays.asList(privateFor));
  }

  public CallPrivateSmartContractFunction callSmartContract(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final Restriction restriction,
      final String privateFrom,
      final List<String> privateFor) {
    return new CallPrivateSmartContractFunction(
        contractAddress,
        encodedFunction,
        transactionSigningKey,
        restriction,
        privateFrom,
        privateFor);
  }

  public CallPrivateSmartContractFunction callSmartContractWithPrivacyGroupId(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final Restriction restriction,
      final String privateFrom,
      final String privacyGroupId) {
    return new CallPrivateSmartContractFunction(
        contractAddress,
        encodedFunction,
        transactionSigningKey,
        restriction,
        privateFrom,
        privacyGroupId);
  }

  public <T extends Contract> LoadPrivateSmartContractTransaction<T> loadSmartContract(
      final String contractAddress,
      final Class<T> clazz,
      final String transactionSigningKey,
      final String privateFrom,
      final String... privateFor) {
    return loadSmartContract(
        contractAddress, clazz, transactionSigningKey, privateFrom, Arrays.asList(privateFor));
  }

  public <T extends Contract> LoadPrivateSmartContractTransaction<T> loadSmartContract(
      final String contractAddress,
      final Class<T> clazz,
      final String transactionSigningKey,
      final String privateFrom,
      final List<String> privateFor) {
    return new LoadPrivateSmartContractTransaction<>(
        contractAddress, clazz, transactionSigningKey, privateFrom, privateFor);
  }

  public <T extends Contract>
      LoadPrivateSmartContractTransactionWithPrivacyGroupId<T> loadSmartContractWithPrivacyGroupId(
          final String contractAddress,
          final Class<T> clazz,
          final String transactionSigningKey,
          final String privateFrom,
          final String privacyGroupId) {
    return new LoadPrivateSmartContractTransactionWithPrivacyGroupId<>(
        contractAddress, clazz, transactionSigningKey, privateFrom, privacyGroupId);
  }

  public CallOnchainPermissioningPrivateSmartContractFunction callOnchainPermissioningSmartContract(
      final String contractAddress,
      final String encodedFunction,
      final String transactionSigningKey,
      final String privateFrom,
      final String privacyGroupId) {
    return new CallOnchainPermissioningPrivateSmartContractFunction(
        contractAddress, encodedFunction, transactionSigningKey, privateFrom, privacyGroupId);
  }
}
