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
package org.hyperledger.besu.consensus.ibft.messagewrappers;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.payload.Authored;
import org.hyperledger.besu.consensus.ibft.payload.Payload;
import org.hyperledger.besu.consensus.ibft.payload.RoundSpecific;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.StringJoiner;

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

  @Override
  public String toString() {
    return new StringJoiner(", ", IbftMessage.class.getSimpleName() + "[", "]")
        .add("payload=" + payload)
        .toString();
  }
}
