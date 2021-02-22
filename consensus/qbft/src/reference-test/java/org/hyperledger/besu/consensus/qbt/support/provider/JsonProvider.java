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

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECP256K1;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.tuweni.bytes.Bytes;

public class JsonProvider {
  private final ObjectMapper objectMapper;

  public JsonProvider() {
    objectMapper = new ObjectMapper();
    addQbftRefTestMappers();
  }

  private void addQbftRefTestMappers() {
    final SimpleModule module =
        new SimpleModule("BesuQbftRefTestJson", new Version(1, 0, 0, null, null, null));
    module.addDeserializer(Bytes.class, new BytesDeserializer());
    module.addSerializer(Bytes.class, new BytesSerializer());

    module.addDeserializer(SECP256K1.Signature.class, new SignatureDeserializer());
    module.addSerializer(SECP256K1.Signature.class, new SignatureSerializer());

    module.addDeserializer(VoteType.class, new VoteTypeDeserializer());
    module.addSerializer(VoteType.class, new VoteTypeSerializer());

    objectMapper
        .registerModules(module, new Jdk8Module())
        .addMixIn(BftExtraData.class, BftExtraDataMixin.class)
        .addMixIn(Vote.class, VoteMixin.class);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
