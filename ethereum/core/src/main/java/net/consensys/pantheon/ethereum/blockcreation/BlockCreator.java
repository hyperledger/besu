package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.core.Block;

public interface BlockCreator {

  Block createBlock(final long timestamp);
}
