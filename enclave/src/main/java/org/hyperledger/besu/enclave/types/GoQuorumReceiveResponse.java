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
package org.hyperledger.besu.enclave.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GoQuorumReceiveResponse {

  private final byte[] payload;
  private final int privacyFlag;
  private final String affectedContractTransactions[];
  private final String execHash;

  @JsonCreator
  public GoQuorumReceiveResponse(
      @JsonProperty(value = "payload") final byte[] payload,
      @JsonProperty(value = "privacyFlag") final int privacyFlag,
      @JsonProperty(value = "affectedContractTransactions")
          final String affectedContractTransactions[],
      @JsonProperty(value = "execHash") final String execHash) {
    this.payload = payload;
    this.privacyFlag = privacyFlag;
    this.affectedContractTransactions = affectedContractTransactions;
    this.execHash = execHash;
  }

  public byte[] getPayload() {
    return payload;
  }

  public int getPrivacyFlag() {
    return privacyFlag;
  }

  public String[] getAffectedContractTransactions() {
    return affectedContractTransactions;
  }

  public String getExecHash() {
    return execHash;
  }
}
