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
package tech.pegasys.pantheon.consensus.ibft.messagewrappers;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.payload.Authored;
import tech.pegasys.pantheon.consensus.ibft.payload.Payload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundSpecific;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class IbftMessage<P extends Payload> implements Authored, RoundSpecific {

  private final SignedData<P> payload;

  public IbftMessage(final SignedData<P> payload) {
    this.payload = payload;
  }

  @Override
  public Address getAuthor() {
    return payload.getAuthor();
  }

  @Override
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return payload.getPayload().getRoundIdentifier();
  }

  public BytesValue encode() {
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    payload.writeTo(rlpOut);
    return rlpOut.encoded();
  }

  public SignedData<P> getSignedPayload() {
    return payload;
  }

  public int getMessageType() {
    return payload.getPayload().getMessageType();
  }

  protected P getPayload() {
    return payload.getPayload();
  }
}
