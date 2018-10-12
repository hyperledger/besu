package tech.pegasys.pantheon.ethereum.blockcreation;

public interface AsyncBlockCreator extends BlockCreator {

  void cancel();
}
