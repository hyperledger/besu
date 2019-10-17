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
package org.hyperledger.besu.crypto.crosschain.threshold.protocol;

import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;

// Crosschain Coordination Contract which sits on the Coordination Blockchain.
// In this PoC the contract stores the group public key.
public class CrosschainCoordinationContract {
  BlsPoint publicKey = null;

  public void setPublicKey(final BlsPoint key) {
    this.publicKey = key;
  }

  public BlsPoint getPublicKey() {
    return this.publicKey;
  }
}
