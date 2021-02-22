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
package org.hyperledger.besu.consensus.qbt.support.extradata;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class BftExtraDataTestCaseSpec {
  final BftExtraData bftExtraData;
  final Bytes rlp;

  @JsonCreator
  public BftExtraDataTestCaseSpec(
      @JsonProperty("qbft_extra_data") final BftExtraData bftExtraData,
      @JsonProperty("rlp") final Bytes rlp) {
    this.bftExtraData = bftExtraData;
    this.rlp = rlp;
  }

  public BftExtraData getBftExtraData() {
    return bftExtraData;
  }

  public Bytes getRlp() {
    return rlp;
  }
}
