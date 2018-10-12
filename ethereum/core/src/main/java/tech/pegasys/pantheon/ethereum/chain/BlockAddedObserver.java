package tech.pegasys.pantheon.ethereum.chain;

@FunctionalInterface
public interface BlockAddedObserver {
  void onBlockAdded(BlockAddedEvent event, Blockchain blockchain);
}
