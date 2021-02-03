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

import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.tuweni.bytes.Bytes;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CommitMessage.class, name = "commit"),
  @JsonSubTypes.Type(value = PrepareMessage.class, name = "prepare"),
  @JsonSubTypes.Type(value = RoundChangeMessage.class, name = "roundChange"),
  @JsonSubTypes.Type(value = ProposalMessage.class, name = "proposal"),
})
public interface RlpTestCaseMessage {

  BftMessage<?> fromRlp(Bytes rlp);

  BftMessage<?> toBftMessage();
}
