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
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

public class CommitPayload extends InRoundPayload {
  private static final int TYPE = IbftV2.COMMIT;
  private final Hash digest;
  private final Signature commitSeal;

  public CommitPayload(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final Signature commitSeal) {
    super(roundIdentifier);
    this.digest = digest;
    this.commitSeal = commitSeal;
  }

  public static CommitPayload readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);
    final Hash digest = readDigest(rlpInput);
    final Signature commitSeal = rlpInput.readBytesValue(Signature::decode);
    rlpInput.leaveList();

    return new CommitPayload(roundIdentifier, digest, commitSeal);
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {

    rlpOutput.startList();
    roundIdentifier.writeTo(rlpOutput);
    rlpOutput.writeBytesValue(digest);
    rlpOutput.writeBytesValue(commitSeal.encodedBytes());
    rlpOutput.endList();
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }

  public Hash getDigest() {
    return digest;
  }

  public Signature getCommitSeal() {
    return commitSeal;
  }
}
