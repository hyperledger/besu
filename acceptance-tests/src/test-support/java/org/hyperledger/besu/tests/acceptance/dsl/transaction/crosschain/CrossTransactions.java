/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.crosschain;

import java.math.BigInteger;

import org.web3j.protocol.besu.crypto.crosschain.BlsThresholdCryptoSystem;

public class CrossTransactions {

  public CrossAddLinkedNode getAddLinkedNode(
      final BigInteger blockchainId, final String ipAddressAndPort) {
    return new CrossAddLinkedNode(blockchainId, ipAddressAndPort);
  }

  public CrossIsLockableTransaction getIsLockable(final String address) {
    return new CrossIsLockableTransaction(address);
  }

  public CrossListLinkedNodes getListLinkedNodes() {
    return new CrossListLinkedNodes();
  }

  public CrossRemoveLinkedNode getRemoveLinkedNode(final BigInteger blockchainId) {
    return new CrossRemoveLinkedNode(blockchainId);
  }

  public CrossActivateKey activateKey(final long keyVersion) {
    return new CrossActivateKey(keyVersion);
  }

  public CrossGetActiveKeyVersion getActiveKeyVersion() {
    return new CrossGetActiveKeyVersion();
  }

  public CrossGetBlockchainPublicKey getBlockchainPublicKey(final long keyVersion) {
    return new CrossGetBlockchainPublicKey(keyVersion);
  }

  public CrossStartThresholdKeyGeneration startThresholdKeyGeneration(
      final int threshold, final BlsThresholdCryptoSystem algorithm) {
    return new CrossStartThresholdKeyGeneration(threshold, algorithm);
  }

  public CrossGetKeyStatus getKeyStatus(final long keyVersion) {
    return new CrossGetKeyStatus(keyVersion);
  }

  public CrossGetKeyActiveNodes getKeyActiveNodes(final long keyVersion) {
    return new CrossGetKeyActiveNodes(keyVersion);
  }

  public CrossGetKeyGenFailureReason getKeyGenFailureReason(final long keyVersion) {
    return new CrossGetKeyGenFailureReason(keyVersion);
  }

  public CrossGetKeyGenNodesDroppedOutOfKeyGeneration getKeyGenNodesDroppedOutOfKeyGeneration(
      final long keyVersion) {
    return new CrossGetKeyGenNodesDroppedOutOfKeyGeneration(keyVersion);
  }

  public CrossListCoordinationContracts listCoordinationContracts() {
    return new CrossListCoordinationContracts();
  }

  public CrossAddCoordinationContract addCoordinationContract(
      final BigInteger blockchainId, final String address, final String ipAddressAndPort) {
    return new CrossAddCoordinationContract(blockchainId, address, ipAddressAndPort);
  }

  public CrossRemoveCoordinationContract removeCoordinationContract(
      final BigInteger blockchainId, final String address) {
    return new CrossRemoveCoordinationContract(blockchainId, address);
  }
}
