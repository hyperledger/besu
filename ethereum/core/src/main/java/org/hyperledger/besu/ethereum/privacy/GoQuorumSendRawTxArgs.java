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
package org.hyperledger.besu.ethereum.privacy;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GoQuorumSendRawTxArgs {

  private final String privateFrom;

  private final List<String> privateFor;

  private final int privacyFlag;

  @JsonCreator
  public GoQuorumSendRawTxArgs(
      @JsonProperty("privateFrom") final String privateFrom,
      @JsonProperty("privateFor") final List<String> privateFor,
      @JsonProperty("privacyFlag") final int privacyFlag) {

    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.privacyFlag = privacyFlag;
  }

  public String getPrivateFrom() {
    return privateFrom;
  }

  public List<String> getPrivateFor() {
    return privateFor;
  }

  public int getPrivacyFlag() {
    return privacyFlag;
  }
}
