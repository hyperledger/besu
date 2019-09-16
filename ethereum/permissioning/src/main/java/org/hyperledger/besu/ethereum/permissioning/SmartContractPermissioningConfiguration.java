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
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.ethereum.core.Address;

public class SmartContractPermissioningConfiguration {
  private boolean smartContractNodeWhitelistEnabled;
  private Address nodeSmartContractAddress;

  private boolean smartContractAccountWhitelistEnabled;
  private Address accountSmartContractAddress;

  public static SmartContractPermissioningConfiguration createDefault() {
    return new SmartContractPermissioningConfiguration();
  }

  public boolean isSmartContractNodeWhitelistEnabled() {
    return smartContractNodeWhitelistEnabled;
  }

  public void setSmartContractNodeWhitelistEnabled(
      final boolean smartContractNodeWhitelistEnabled) {
    this.smartContractNodeWhitelistEnabled = smartContractNodeWhitelistEnabled;
  }

  public Address getNodeSmartContractAddress() {
    return nodeSmartContractAddress;
  }

  public void setNodeSmartContractAddress(final Address nodeSmartContractAddress) {
    this.nodeSmartContractAddress = nodeSmartContractAddress;
  }

  public boolean isSmartContractAccountWhitelistEnabled() {
    return smartContractAccountWhitelistEnabled;
  }

  public void setSmartContractAccountWhitelistEnabled(
      final boolean smartContractAccountWhitelistEnabled) {
    this.smartContractAccountWhitelistEnabled = smartContractAccountWhitelistEnabled;
  }

  public Address getAccountSmartContractAddress() {
    return accountSmartContractAddress;
  }

  public void setAccountSmartContractAddress(final Address accountSmartContractAddress) {
    this.accountSmartContractAddress = accountSmartContractAddress;
  }
}
