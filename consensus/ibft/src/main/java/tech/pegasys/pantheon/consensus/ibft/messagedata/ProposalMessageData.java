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
package tech.pegasys.pantheon.consensus.ibft.messagedata;

import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class ProposalMessageData extends AbstractIbftMessageData {

  private static final int MESSAGE_CODE = IbftV2.PROPOSAL;

  private ProposalMessageData(final BytesValue data) {
    super(data);
  }

  public static ProposalMessageData fromMessageData(final MessageData messageData) {
    return fromMessageData(
        messageData, MESSAGE_CODE, ProposalMessageData.class, ProposalMessageData::new);
  }

  public Proposal decode() {
    return Proposal.decode(data);
  }

  public static ProposalMessageData create(final Proposal proposal) {
    return new ProposalMessageData(proposal.encode());
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }
}
