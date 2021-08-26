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
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.datatypes.Address;

public class SmartContractPermissioningConfiguration {
  private boolean smartContractNodeAllowlistEnabled;
  private Address nodeSmartContractAddress;
  private int nodeSmartContractInterfaceVersion = 1;

  private boolean smartContractAccountAllowlistEnabled;
  private Address accountSmartContractAddress;

  public static SmartContractPermissioningConfiguration createDefault() {
    return new SmartContractPermissioningConfiguration();
  }

  public boolean isSmartContractNodeAllowlistEnabled() {
    return smartContractNodeAllowlistEnabled;
  }

  public void setSmartContractNodeAllowlistEnabled(
      final boolean smartContractNodeAllowlistEnabled) {
    this.smartContractNodeAllowlistEnabled = smartContractNodeAllowlistEnabled;
  }

  public Address getNodeSmartContractAddress() {
    return nodeSmartContractAddress;
  }

  public void setNodeSmartContractAddress(final Address nodeSmartContractAddress) {
    this.nodeSmartContractAddress = nodeSmartContractAddress;
  }

  public boolean isSmartContractAccountAllowlistEnabled() {
    return smartContractAccountAllowlistEnabled;
  }

  public void setSmartContractAccountAllowlistEnabled(
      final boolean smartContractAccountAllowlistEnabled) {
    this.smartContractAccountAllowlistEnabled = smartContractAccountAllowlistEnabled;
  }

  public Address getAccountSmartContractAddress() {
    return accountSmartContractAddress;
  }

  public void setAccountSmartContractAddress(final Address accountSmartContractAddress) {
    this.accountSmartContractAddress = accountSmartContractAddress;
  }

  public void setNodeSmartContractInterfaceVersion(final int nodeSmartContractInterfaceVersion) {
    this.nodeSmartContractInterfaceVersion = nodeSmartContractInterfaceVersion;
  }

  public int getNodeSmartContractInterfaceVersion() {
    return nodeSmartContractInterfaceVersion;
  }
}
