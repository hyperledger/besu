package org.hyperledger.besu.consensus.qbt;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.payload.CommitPayload;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.tuweni.bytes.Bytes;

public class RlpTestCaseSpec {

  private final RlpTestInput input;
  private final String output;

  @JsonCreator
  public RlpTestCaseSpec(
      @JsonProperty("input") final RlpTestInput input,
      @JsonProperty("output") final String output) {
    this.input = input;
    this.output = output;
  }

  public RlpTestInput getInput() {
    return input;
  }

  public String getOutput() {
    return output;
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CommitMessage.class, name = "commit"),
    @JsonSubTypes.Type(value = PrepareMessage.class, name = "prepare"),
    @JsonSubTypes.Type(value = RoundChangeMessage.class, name = "roundchange"),
    @JsonSubTypes.Type(value = ProposalMessage.class, name = "proposal"),
  })
  public interface RlpTestInput {

    RlpTestInput fromRlp(Bytes rlp);

    Bytes toRlp();
  }

  public static class UnsignedCommit {

    private final long sequence;
    private final int round;
    private final String commitSeal;
    private final String digest;

    @JsonCreator
    public UnsignedCommit(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("commitSeal") final String commitSeal,
        @JsonProperty("digest") final String digest) {
      this.sequence = sequence;
      this.round = round;
      this.commitSeal = commitSeal;
      this.digest = digest;
    }
  }

  public static class CommitMessage implements RlpTestInput {

    private final UnsignedCommit unsignedCommit;
    private final String signature;

    @JsonCreator
    public CommitMessage(
        @JsonProperty("unsignedCommit") UnsignedCommit unsignedCommit,
        @JsonProperty("signature") final String signature) {
      this.unsignedCommit = unsignedCommit;
      this.signature = signature;
    }

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      final Commit commit = Commit.decode(rlp);
      return new CommitMessage(
          new UnsignedCommit(
              commit.getRoundIdentifier().getSequenceNumber(),
              commit.getRoundIdentifier().getRoundNumber(),
              commit.getCommitSeal().encodedBytes().toHexString(),
              commit.getDigest().toHexString()),
          commit.getSignedPayload().getSignature().encodedBytes().toHexString());
    }

    @Override
    public Bytes toRlp() {
      final CommitPayload commitPayload =
          new CommitPayload(
              new ConsensusRoundIdentifier(unsignedCommit.sequence, unsignedCommit.round),
              Hash.fromHexStringLenient(unsignedCommit.digest),
              Signature.decode(Bytes.fromHexStringLenient(unsignedCommit.commitSeal)));

      final SignedData<CommitPayload> signedCommitPayload =
          SignedData.create(commitPayload, Signature.decode(Bytes.fromHexString(signature)));

      return signedCommitPayload.encode();
    }
  }

  public static class PrepareMessage implements RlpTestInput {

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      return null;
    }

    @Override
    public Bytes toRlp() {
      return null;
    }
  }

  public static class RoundChangeMessage implements RlpTestInput {

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      return null;
    }

    @Override
    public Bytes toRlp() {
      return null;
    }
  }

  public static class ProposalMessage implements RlpTestInput {

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      return null;
    }

    @Override
    public Bytes toRlp() {
      return null;
    }
  }
}
