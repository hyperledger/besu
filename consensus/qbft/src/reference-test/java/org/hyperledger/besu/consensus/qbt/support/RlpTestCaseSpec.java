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
package org.hyperledger.besu.consensus.qbt.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RlpTestCaseSpec {
  private final RlpTestCaseMessage message;
  private final String rlp;

  @JsonCreator
  public RlpTestCaseSpec(
      @JsonProperty("message") final RlpTestCaseMessage message,
      @JsonProperty("rlp") final String rlp) {
    this.message = message;
    this.rlp = rlp;
  }

  public RlpTestCaseMessage getMessage() {
    return message;
  }

  public String getRlp() {
    return rlp;
  }
}
