/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.ibftmessagedata;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.Optional;

public class IbftUnsignedRoundChangeMessageData extends AbstractIbftUnsignedMessageData {

  private static final int TYPE = IbftV2.PREPARE;

  private final ConsensusRoundIdentifier roundChangeIdentifier;

  // The validator may not hae any prepared certificate
  private final Optional<IbftPreparedCertificate> preparedCertificate;

  public IbftUnsignedRoundChangeMessageData(
      final ConsensusRoundIdentifier roundIdentifier,
      final Optional<IbftPreparedCertificate> preparedCertificate) {
    this.roundChangeIdentifier = roundIdentifier;
    this.preparedCertificate = preparedCertificate;
  }

  public ConsensusRoundIdentifier getRoundChangeIdentifier() {
    return roundChangeIdentifier;
  }

  public Optional<IbftPreparedCertificate> getPreparedCertificate() {
    return preparedCertificate;
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    // RLP encode of the message data content (round identifier and prepared certificate)
    BytesValueRLPOutput ibftMessage = new BytesValueRLPOutput();
    ibftMessage.startList();
    roundChangeIdentifier.writeTo(ibftMessage);

    if (preparedCertificate.isPresent()) {
      preparedCertificate.get().writeTo(ibftMessage);
    } else {
      ibftMessage.writeNull();
    }
    ibftMessage.endList();
  }

  public static IbftUnsignedRoundChangeMessageData readFrom(final RLPInput rlpInput) {
    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);

    final Optional<IbftPreparedCertificate> preparedCertificate;

    if (rlpInput.nextIsNull()) {
      rlpInput.skipNext();
      preparedCertificate = Optional.empty();
    } else {
      preparedCertificate = Optional.of(IbftPreparedCertificate.readFrom(rlpInput));
    }
    rlpInput.leaveList();

    return new IbftUnsignedRoundChangeMessageData(roundIdentifier, preparedCertificate);
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }
}
