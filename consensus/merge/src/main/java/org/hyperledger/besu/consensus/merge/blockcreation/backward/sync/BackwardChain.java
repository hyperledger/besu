package org.hyperledger.besu.consensus.merge.blockcreation.backward.sync;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BackwardChain {
  private static final Logger LOG = LogManager.getLogger();
  private final List<BlockHeader> ancestors = new ArrayList<>();

  public BackwardChain(final BlockHeader pivot) {
    ancestors.add(pivot);
  }

  public BlockHeader getFirstHeader() {
    return ancestors.get(ancestors.size() - 1);
  }

  public void saveHeader(final BlockHeader blockHeader) {
    BlockHeader firstHeader = getFirstHeader();
    if (firstHeader.getNumber() != blockHeader.getNumber() - 1) {
      throw new RuntimeException("Wrong height of header");
    }
    if (!firstHeader.getParentHash().equals(blockHeader.getHash())) {
      throw new RuntimeException("Hash of header does not match our expectations");
    }
    ancestors.add(blockHeader);
    LOG.debug(
        "Added header {} on height {} to backward chain led by pivot {} on height {}",
        () -> blockHeader.getHash().toString().substring(0, 20),
        blockHeader::getNumber,
        () -> firstHeader.getHash().toString().substring(0, 20),
        firstHeader::getNumber);
  }

  public void merge(final BackwardChain historicalBackwardChain) {
    BlockHeader firstHeader = getFirstHeader();
    BlockHeader historicalPivot = historicalBackwardChain.getPivot();
    BlockHeader pivot = getPivot();
    if (firstHeader.equals(historicalPivot)) {
      this.ancestors.addAll(historicalBackwardChain.ancestors);
      LOG.debug(
          "Merged backward chain led by block {} into chain led by block {}, new backward chain starts at height {} and ends at height {}",
          () -> historicalPivot.getHash().toString().substring(0, 20),
          () -> pivot.getHash().toString().substring(0, 20),
          pivot::getNumber,
          () -> getFirstHeader().getNumber());
    } else {
      LOG.warn(
          "Cannot merge previous historical run because headers of {} and {} do not equal. Ignoring previous run. Did someone lie to us?",
          () -> firstHeader.getHash().toString().substring(0, 20),
          () -> historicalPivot.getHash().toString().substring(0, 20));
    }
  }

  private BlockHeader getPivot() {
    return ancestors.get(0);
  }

  public void dropFirstHeader() {
    ancestors.remove(ancestors.size() - 1);
  }
}
