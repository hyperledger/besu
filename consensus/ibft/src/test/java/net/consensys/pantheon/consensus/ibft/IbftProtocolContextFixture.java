package net.consensys.pantheon.consensus.ibft;

import static java.util.Arrays.asList;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Address;

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
