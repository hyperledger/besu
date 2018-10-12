package tech.pegasys.pantheon.consensus.ibft;

import static java.util.Arrays.asList;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.List;

public class IbftProtocolContextFixture {

  public static ProtocolContext<IbftContext> protocolContext(final Address... validators) {
    return protocolContext(asList(validators));
  }

  public static ProtocolContext<IbftContext> protocolContext(final List<Address> validators) {
    return new ProtocolContext<>(
        null, null, new IbftContext(new VoteTally(validators), new VoteProposer()));
  }
}
