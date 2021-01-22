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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.goquorum.GoQuorumPrivateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class GoQuorumPrivacyParameters {

  private final GoQuorumEnclave enclave;

  private final String enclaveKey;

  private final GoQuorumPrivateStorage goQuorumPrivateStorage;
  private final WorldStateArchive privateWorldStateArchive;

  public GoQuorumPrivacyParameters(
      final GoQuorumEnclave enclave,
      final String enclaveKey,
      final GoQuorumPrivateStorage goQuorumPrivateStorage,
      final WorldStateArchive privateWorldStateArchive) {
    this.enclave = enclave;
    this.enclaveKey = enclaveKey;
    this.goQuorumPrivateStorage = goQuorumPrivateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  public GoQuorumEnclave enclave() {
    return enclave;
  }

  public String enclaveKey() {
    return enclaveKey;
  }

  public GoQuorumPrivateStorage privateStorage() {
    return goQuorumPrivateStorage;
  }

  public WorldStateArchive worldStateArchive() {
    return privateWorldStateArchive;
  }
}
