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
package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

abstract class BftExtraDataMixin {
  @JsonCreator
  BftExtraDataMixin(
      @JsonProperty("vanityData") final Bytes vanityData,
      @JsonProperty("seals") final Collection<SECP256K1.Signature> seals,
      @JsonProperty("vote") final Optional<Vote> vote,
      @JsonProperty("round") final int round,
      @JsonProperty("validators") final Collection<Address> validators) {}
}
