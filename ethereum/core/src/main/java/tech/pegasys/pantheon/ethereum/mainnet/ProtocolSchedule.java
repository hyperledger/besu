package tech.pegasys.pantheon.ethereum.mainnet;

public interface ProtocolSchedule<C> {

  ProtocolSpec<C> getByBlockNumber(long number);
}
