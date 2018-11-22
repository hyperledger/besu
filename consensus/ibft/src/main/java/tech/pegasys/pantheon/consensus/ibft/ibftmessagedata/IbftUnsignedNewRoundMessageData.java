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
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

public class IbftUnsignedNewRoundMessageData extends AbstractIbftUnsignedMessageData {

  private static final int TYPE = IbftV2.NEW_ROUND;

  private final ConsensusRoundIdentifier roundChangeIdentifier;

  private final IbftRoundChangeCertificate roundChangeCertificate;

  private final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> ibftPrePrepareMessage;

  public IbftUnsignedNewRoundMessageData(
      final ConsensusRoundIdentifier roundIdentifier,
      final IbftRoundChangeCertificate roundChangeCertificate,
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> ibftPrePrepareMessage) {
    this.roundChangeIdentifier = roundIdentifier;
    this.roundChangeCertificate = roundChangeCertificate;
    this.ibftPrePrepareMessage = ibftPrePrepareMessage;
  }

  public ConsensusRoundIdentifier getRoundChangeIdentifier() {
    return roundChangeIdentifier;
  }

  public IbftRoundChangeCertificate getRoundChangeCertificate() {
    return roundChangeCertificate;
  }

  public IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> getIbftPrePrepareMessage() {
    return ibftPrePrepareMessage;
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    // RLP encode of the message data content (round identifier and prepared certificate)
    rlpOutput.startList();
    roundChangeIdentifier.writeTo(rlpOutput);
    roundChangeCertificate.writeTo(rlpOutput);
    ibftPrePrepareMessage.writeTo(rlpOutput);
    rlpOutput.endList();
  }

  public static IbftUnsignedNewRoundMessageData readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);
    final IbftRoundChangeCertificate roundChangeCertificate =
        IbftRoundChangeCertificate.readFrom(rlpInput);
    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> ibftPrePrepareMessage =
        IbftSignedMessageData.readIbftSignedPrePrepareMessageDataFrom(rlpInput);
    rlpInput.leaveList();

    return new IbftUnsignedNewRoundMessageData(
        roundIdentifier, roundChangeCertificate, ibftPrePrepareMessage);
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }
}
