package net.consensys.pantheon.ethereum.mainnet;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.TreeSet;

public class MutableProtocolSchedule<C> implements ProtocolSchedule<C> {

  private final NavigableSet<ScheduledProtocolSpec<C>> protocolSpecs =
      new TreeSet<>(
          Comparator.<ScheduledProtocolSpec<C>, Long>comparing(ScheduledProtocolSpec::getBlock)
              .reversed());

  public void putMilestone(final long blockNumber, final ProtocolSpec<C> protocolSpec) {
    final ScheduledProtocolSpec<C> scheduledProtocolSpec =
        new ScheduledProtocolSpec<>(blockNumber, protocolSpec);
    // Ensure this replaces any existing spec at the same block number.
    protocolSpecs.remove(scheduledProtocolSpec);
    protocolSpecs.add(scheduledProtocolSpec);
  }

  @Override
  public ProtocolSpec<C> getByBlockNumber(final long number) {
    checkArgument(number >= 0, "number must be non-negative");
    checkArgument(
        !protocolSpecs.isEmpty(), "At least 1 milestone must be provided to the protocol schedule");
    checkArgument(
        protocolSpecs.last().getBlock() == 0, "There must be a milestone starting from block 0");
    // protocolSpecs is sorted in descending block order, so the first one we find that's lower than
    // the requested level will be the most appropriate spec
    for (final ScheduledProtocolSpec<C> s : protocolSpecs) {
      if (number >= s.getBlock()) {
        return s.getSpec();
      }
    }
    return null;
  }
}
