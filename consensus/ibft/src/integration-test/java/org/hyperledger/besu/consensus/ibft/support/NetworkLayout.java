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
package org.hyperledger.besu.consensus.ibft.support;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.Iterables;

public class NetworkLayout {

  private final NodeParams localNode;
  private final TreeMap<Address, NodeParams> addressKeyMap;
  private final List<NodeParams> remotePeers;

  public NetworkLayout(
      final NodeParams localNode, final TreeMap<Address, NodeParams> addressKeyMap) {
    this.localNode = localNode;
    this.addressKeyMap = addressKeyMap;
    this.remotePeers = new ArrayList<>(addressKeyMap.values());
    this.remotePeers.remove(localNode);
  }

  public static NetworkLayout createNetworkLayout(
      final int validatorCount, final int firstLocalNodeBlockNum) {
    final TreeMap<Address, NodeParams> addressKeyMap = createValidators(validatorCount);

    final NodeParams localNode = Iterables.get(addressKeyMap.values(), firstLocalNodeBlockNum);

    return new NetworkLayout(localNode, addressKeyMap);
  }

  private static TreeMap<Address, NodeParams> createValidators(final int validatorCount) {
    // Map is required to be sorted by address
    final TreeMap<Address, NodeParams> addressKeyMap = new TreeMap<>();

    for (int i = 0; i < validatorCount; i++) {
      final KeyPair newKeyPair = KeyPair.generate();
      final Address nodeAddress = Util.publicKeyToAddress(newKeyPair.getPublicKey());
      addressKeyMap.put(nodeAddress, new NodeParams(nodeAddress, newKeyPair));
    }

    return addressKeyMap;
  }

  public Set<Address> getValidatorAddresses() {
    return addressKeyMap.keySet();
  }

  public NodeParams getLocalNode() {
    return localNode;
  }

  public List<NodeParams> getRemotePeers() {
    return remotePeers;
  }
}
