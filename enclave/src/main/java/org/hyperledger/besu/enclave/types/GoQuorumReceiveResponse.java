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

/** The GoQuorum receive response. */
public class GoQuorumReceiveResponse {

  private final byte[] payload;
  private final int privacyFlag;
  private final String affectedContractTransactions[];
  private final String execHash;

  /**
   * Instantiates a new GoQuorum receive response.
   *
   * @param payload the payload
   * @param privacyFlag the privacy flag
   * @param affectedContractTransactions the affected contract transactions
   * @param execHash the exec hash
   */
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

  /**
   * Get payload.
   *
   * @return the byte [ ]
   */
  public byte[] getPayload() {
    return payload;
  }

  /**
   * Gets privacy flag.
   *
   * @return the privacy flag
   */
  public int getPrivacyFlag() {
    return privacyFlag;
  }

  /**
   * Get affected contract transactions.
   *
   * @return the string [ ]
   */
  public String[] getAffectedContractTransactions() {
    return affectedContractTransactions;
  }

  /**
   * Gets exec hash.
   *
   * @return the exec hash
   */
  public String getExecHash() {
    return execHash;
  }
}
