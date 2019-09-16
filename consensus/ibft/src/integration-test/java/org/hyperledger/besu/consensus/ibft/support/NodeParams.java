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

public class NodeParams {
  private final Address address;
  private final KeyPair nodeKeys;

  public NodeParams(final Address address, final KeyPair nodeKeys) {
    this.address = address;
    this.nodeKeys = nodeKeys;
  }

  public Address getAddress() {
    return address;
  }

  public KeyPair getNodeKeyPair() {
    return nodeKeys;
  }
}
