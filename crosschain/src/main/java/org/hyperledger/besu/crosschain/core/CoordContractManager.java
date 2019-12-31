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
package org.hyperledger.besu.crosschain.core;

import org.hyperledger.besu.ethereum.core.Address;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CoordContractManager {
  private Map<String, CoordinationContractInformation> coordinationContracts = new HashMap<>();

  public void addCoordinationContract(
      final BigInteger coordinationBlockchainId,
      final Address coodinationContractAddress,
      final String ipAddressAndPort) {
    this.coordinationContracts.put(
        key(coordinationBlockchainId, coodinationContractAddress),
        new CoordinationContractInformation(
            coordinationBlockchainId, coodinationContractAddress, ipAddressAndPort));
  }

  public void removeCoordinationContract(
      final BigInteger coordinationBlockchainId, final Address coodinationContractAddress) {
    this.coordinationContracts.remove(key(coordinationBlockchainId, coodinationContractAddress));
  }

  public Collection<CoordinationContractInformation> getAllCoordinationContracts() {
    return this.coordinationContracts.values();
  }

  public CoordinationContractInformation get(
      final BigInteger coordinationBlockchainId, final Address coodinationContractAddress) {
    return this.coordinationContracts.get(
        key(coordinationBlockchainId, coodinationContractAddress));
  }

  public String getIpAndPort(
      final BigInteger coordinationBlockchainId, final Address coodinationContractAddress) {
    CoordinationContractInformation info =
        this.coordinationContracts.get(key(coordinationBlockchainId, coodinationContractAddress));
    return (info != null) ? info.ipAddressAndPort : null;
  }

  private String key(
      final BigInteger coordinationBlockchainId, final Address coodinationContractAddress) {
    return coordinationBlockchainId.toString(16) + coodinationContractAddress.getHexString();
  }
}
