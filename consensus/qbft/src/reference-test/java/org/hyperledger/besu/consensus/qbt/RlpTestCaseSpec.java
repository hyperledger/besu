package org.hyperledger.besu.consensus.qbt;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.CommitPayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    @JsonSubTypes.Type(value = RoundChangeMessage.class, name = "roundChange"),
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
              Signature.decode(Bytes.fromHexString(unsignedCommit.commitSeal)));
      final SignedData<CommitPayload> signedCommitPayload =
          SignedData.create(commitPayload, Signature.decode(Bytes.fromHexString(signature)));
      return signedCommitPayload.encode();
    }
  }

  public static class UnsignedPrepare {
    private final long sequence;
    private final int round;
    private final String digest;

    @JsonCreator
    public UnsignedPrepare(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("digest") final String digest) {
      this.sequence = sequence;
      this.round = round;
      this.digest = digest;
    }
  }

  // TODO dupe here, this is the same as PrepareMessage
  public static class SignedPrepare {
    private final UnsignedPrepare unsignedPrepare;
    private final String signature;

    @JsonCreator
    public SignedPrepare(
        @JsonProperty("unsignedPrepare") UnsignedPrepare unsignedPrepare,
        @JsonProperty("signature") final String signature) {
      this.unsignedPrepare = unsignedPrepare;
      this.signature = signature;
    }
  }

  public static class PrepareMessage implements RlpTestInput {
    private final UnsignedPrepare unsignedPrepare;
    private final String signature;

    @JsonCreator
    public PrepareMessage(
        @JsonProperty("unsignedPrepare") UnsignedPrepare unsignedPrepare,
        @JsonProperty("signature") final String signature) {
      this.unsignedPrepare = unsignedPrepare;
      this.signature = signature;
    }

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      final Prepare prepare = Prepare.decode(rlp);
      return new PrepareMessage(
          new UnsignedPrepare(
              prepare.getRoundIdentifier().getSequenceNumber(),
              prepare.getRoundIdentifier().getRoundNumber(),
              prepare.getDigest().toHexString()),
          prepare.getSignedPayload().getSignature().encodedBytes().toHexString());
    }

    @Override
    public Bytes toRlp() {
      final PreparePayload preparePayload =
          new PreparePayload(
              new ConsensusRoundIdentifier(unsignedPrepare.sequence, unsignedPrepare.round),
              Hash.fromHexStringLenient(unsignedPrepare.digest));
      final SignedData<PreparePayload> signedPreparePayload =
          SignedData.create(preparePayload, Signature.decode(Bytes.fromHexString(signature)));
      return new Prepare(signedPreparePayload).encode();
    }
  }

  public static class UnsignedRoundChange {
    private final long sequence;
    private final int round;
    private final Optional<String> preparedValue;
    private final Optional<Integer> preparedRound;

    @JsonCreator
    public UnsignedRoundChange(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("preparedValue") final Optional<String> preparedValue,
        @JsonProperty("preparedRound") final Optional<Integer> preparedRound) {
      this.sequence = sequence;
      this.round = round;
      this.preparedValue = preparedValue;
      this.preparedRound = preparedRound;
    }
  }

  public static class SignedRoundChange {
    private final UnsignedRoundChange unsignedRoundChange;
    private final String signature;

    public SignedRoundChange(
        @JsonProperty("unsignedRoundChange") UnsignedRoundChange unsignedRoundChange,
        @JsonProperty("signature") final String signature) {
      this.unsignedRoundChange = unsignedRoundChange;
      this.signature = signature;
    }
  }

  public static class RoundChangeMessage implements RlpTestInput {
    private final SignedRoundChange signedRoundChange;
    private final Optional<String> block;
    private final List<SignedPrepare> prepares;

    public RoundChangeMessage(
        @JsonProperty("signedRoundChange") SignedRoundChange signedRoundChange,
        @JsonProperty("block") final Optional<String> block,
        @JsonProperty("prepares") final List<SignedPrepare> prepares) {
      this.signedRoundChange = signedRoundChange;
      this.block = block;
      this.prepares = prepares;
    }

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      final RoundChange roundChange = RoundChange.decode(rlp);
      final UnsignedRoundChange unsignedRoundChange =
          new UnsignedRoundChange(
              roundChange.getRoundIdentifier().getSequenceNumber(),
              roundChange.getRoundIdentifier().getRoundNumber(),
              roundChange
                  .getPreparedRoundMetadata()
                  .map(rm -> rm.getPreparedBlockHash().toHexString()),
              roundChange.getPreparedRoundMetadata().map(PreparedRoundMetadata::getPreparedRound));
      final List<SignedPrepare> prepares =
          roundChange.getPrepares().stream()
              .map(
                  p ->
                      new SignedPrepare(
                          new UnsignedPrepare(
                              p.getPayload().getRoundIdentifier().getSequenceNumber(),
                              p.getPayload().getRoundIdentifier().getRoundNumber(),
                              p.getPayload().getDigest().toHexString()),
                          p.getSignature().encodedBytes().toHexString()))
              .collect(Collectors.toList());
      return new RoundChangeMessage(
          new SignedRoundChange(
              unsignedRoundChange,
              roundChange.getSignedPayload().getSignature().encodedBytes().toHexString()),
          roundChange.getProposedBlock().map(b -> b.toRlp().toHexString()),
          prepares);
    }

    @Override
    public Bytes toRlp() {
      final UnsignedRoundChange unsignedRoundChange = signedRoundChange.unsignedRoundChange;
      final Optional<PreparedRoundMetadata> preparedRoundMetadata =
          unsignedRoundChange.preparedRound.isPresent()
                  && unsignedRoundChange.preparedValue.isPresent()
              ? Optional.of(
                  new PreparedRoundMetadata(
                      Hash.fromHexString(unsignedRoundChange.preparedValue.get()),
                      unsignedRoundChange.preparedRound.get()))
              : Optional.empty();
      final RoundChangePayload roundChangePayload =
          new RoundChangePayload(
              new ConsensusRoundIdentifier(unsignedRoundChange.sequence, unsignedRoundChange.round),
              preparedRoundMetadata);
      final SignedData<RoundChangePayload> signedRoundChangePayload =
          SignedData.create(
              roundChangePayload,
              Signature.decode(Bytes.fromHexString(signedRoundChange.signature)));
      final Optional<Block> block =
          this.block.map(
              b ->
                  Block.readFrom(
                      RLP.input(Bytes.fromHexString(b)),
                      BftBlockHeaderFunctions.forOnChainBlock()));
      final List<SignedData<PreparePayload>> signedPrepares =
          prepares.stream()
              .map(
                  p ->
                      SignedData.create(
                          new PreparePayload(
                              new ConsensusRoundIdentifier(
                                  p.unsignedPrepare.sequence, p.unsignedPrepare.round),
                              Hash.fromHexString(p.unsignedPrepare.digest)),
                          Signature.decode(Bytes.fromHexString(p.signature))))
              .collect(Collectors.toList());
      return new RoundChange(signedRoundChangePayload, block, signedPrepares).encode();
    }
  }

  public static class UnsignedProposal {
    private final long sequence;
    private final int round;
    private final String block;

    @JsonCreator
    public UnsignedProposal(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("block") final String block) {
      this.sequence = sequence;
      this.round = round;
      this.block = block;
    }
  }

  public static class SignedProposal {
    private final UnsignedProposal unsignedProposal;
    private final String signature;

    public SignedProposal(
        @JsonProperty("unsignedProposal") UnsignedProposal unsignedProposal,
        @JsonProperty("signature") final String signature) {
      this.unsignedProposal = unsignedProposal;
      this.signature = signature;
    }
  }

  public static class ProposalMessage implements RlpTestInput {
    private final SignedProposal signedProposal;
    private final List<SignedRoundChange> roundChanges;
    private final List<SignedPrepare> prepares;

    public ProposalMessage(
        @JsonProperty("signedProposal") SignedProposal signedProposal,
        @JsonProperty("roundChanges") List<SignedRoundChange> roundChanges,
        @JsonProperty("prepares") final List<SignedPrepare> prepares) {
      this.signedProposal = signedProposal;
      this.roundChanges = roundChanges;
      this.prepares = prepares;
    }

    @Override
    public RlpTestInput fromRlp(final Bytes rlp) {
      final Proposal proposal = Proposal.decode(rlp);
      final SignedProposal signedProposal =
          new SignedProposal(
              new UnsignedProposal(
                  proposal.getRoundIdentifier().getSequenceNumber(),
                  proposal.getRoundIdentifier().getRoundNumber(),
                  proposal.getBlock().toRlp().toHexString()),
              proposal.getSignedPayload().getSignature().encodedBytes().toHexString());
      final List<SignedRoundChange> signedRoundChanges =
          proposal.getRoundChanges().stream()
              .map(
                  r ->
                      new SignedRoundChange(
                          new UnsignedRoundChange(
                              r.getPayload().getRoundIdentifier().getSequenceNumber(),
                              r.getPayload().getRoundIdentifier().getRoundNumber(),
                              r.getPayload()
                                  .getPreparedRoundMetadata()
                                  .map(rm -> rm.getPreparedBlockHash().toHexString()),
                              r.getPayload()
                                  .getPreparedRoundMetadata()
                                  .map(PreparedRoundMetadata::getPreparedRound)),
                          r.getSignature().encodedBytes().toHexString()))
              .collect(Collectors.toList());
      final List<SignedPrepare> signedPrepares =
          proposal.getPrepares().stream()
              .map(
                  p ->
                      new SignedPrepare(
                          new UnsignedPrepare(
                              p.getPayload().getRoundIdentifier().getSequenceNumber(),
                              p.getPayload().getRoundIdentifier().getRoundNumber(),
                              p.getPayload().getDigest().toHexString()),
                          p.getSignature().encodedBytes().toHexString()))
              .collect(Collectors.toList());
      return new ProposalMessage(signedProposal, signedRoundChanges, signedPrepares);
    }

    @Override
    public Bytes toRlp() {
      final List<SignedData<RoundChangePayload>> signedRoundChanges =
          roundChanges.stream()
              .map(
                  r ->
                      SignedData.create(
                          new RoundChangePayload(
                              new ConsensusRoundIdentifier(
                                  r.unsignedRoundChange.sequence, r.unsignedRoundChange.round),
                              r.unsignedRoundChange.preparedRound.isPresent()
                                      && r.unsignedRoundChange.preparedValue.isPresent()
                                  ? Optional.of(
                                      new PreparedRoundMetadata(
                                          Hash.fromHexString(
                                              r.unsignedRoundChange.preparedValue.get()),
                                          r.unsignedRoundChange.preparedRound.get()))
                                  : Optional.empty()),
                          Signature.decode(Bytes.fromHexString(r.signature))))
              .collect(Collectors.toList());

      final List<SignedData<PreparePayload>> signedPrepares =
          prepares.stream()
              .map(
                  p ->
                      SignedData.create(
                          new PreparePayload(
                              new ConsensusRoundIdentifier(
                                  p.unsignedPrepare.sequence, p.unsignedPrepare.round),
                              Hash.fromHexString(p.unsignedPrepare.digest)),
                          Signature.decode(Bytes.fromHexString(p.signature))))
              .collect(Collectors.toList());

      final Block block =
          Block.readFrom(
              RLP.input(Bytes.fromHexString(signedProposal.unsignedProposal.block)),
              BftBlockHeaderFunctions.forOnChainBlock());
      final ProposalPayload proposalPayload =
          new ProposalPayload(
              new ConsensusRoundIdentifier(
                  signedProposal.unsignedProposal.sequence, signedProposal.unsignedProposal.round),
              block);

      final SignedData<ProposalPayload> signedProposalPayload =
          SignedData.create(
              proposalPayload, Signature.decode(Bytes.fromHexString(signedProposal.signature)));
      return new Proposal(signedProposalPayload, signedRoundChanges, signedPrepares).encode();
    }
  }
}
