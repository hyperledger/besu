package net.consensys.pantheon.ethereum.blockcreation;

public interface AsyncBlockCreator extends BlockCreator {

  void cancel();
}
