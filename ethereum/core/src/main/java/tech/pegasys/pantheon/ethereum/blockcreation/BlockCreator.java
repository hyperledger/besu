package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.core.Block;

public interface BlockCreator {

  Block createBlock(final long timestamp);
}
