package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PmtTransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Hash, PmtTransactionTracker> pmtPool;

  public PmtTransactionPool() {
    LOG.info("creating a new PmtTransactionPool");
    this.pmtPool = new HashMap<>();
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    // remove from the map any transactions that went into this block
    // TODO take into account re-orgs
    event
        .getAddedTransactions()
        .forEach(
            tx -> {
              LOG.info("removing " + pmtPool.containsKey(tx.getHash()));
              pmtPool.remove(tx.getHash());
            });
  }

  public long getCountMatchingPmt(final String sender, final String privacyGroupId) {

    // TODO take into account the nonce - get the max nonce from this collection:
    return pmtPool.values().stream()
        .filter(
            pmt ->
                pmt.getSender().equals(sender)
                    && pmt.getPrivacyGroupIdBase64().equals(privacyGroupId))
        .count();
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash, final PrivateTransaction privateTx, final String privacyGroupId) {
    LOG.info("size of pmtPool = {}", pmtPool.size());
    return addPmtTransactionTracker(
        pmtHash, privateTx.sender.toHexString(), privacyGroupId, privateTx.getNonce());
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash, final String sender, final String privacyGroupId, final long nonce) {

    final PmtTransactionTracker pmtTracker = new PmtTransactionTracker(sender, privacyGroupId, nonce);
    pmtPool.put(pmtHash, pmtTracker);
    LOG.info(
        "adding pmtPool tracker: pmtHash: {} pmtTracker {}",
        pmtHash,
        pmtTracker);
    return pmtHash;
  }

  protected static class PmtTransactionTracker {
    private final String sender;
    private final String privacyGroupIdBase64;
    private final long nonce;

    protected PmtTransactionTracker(
        final String sender, final String privacyGroupIdBase64, final long nonce) {
      this.sender = sender;
      this.privacyGroupIdBase64 = privacyGroupIdBase64;
      this.nonce = nonce;
    }

    public String getSender() {
      return sender;
    }

    public String getPrivacyGroupIdBase64() {
      return privacyGroupIdBase64;
    }

    public long getNonce() {
      return nonce;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("PmtTransactionTracker ").append("{");
      sb.append("sender=").append(getSender()).append(", ");
      sb.append("privacyGroupId=").append(getPrivacyGroupIdBase64()).append(", ");
      sb.append("nonce=").append(getNonce()).append(", ");
      return sb.append("}").toString();
    }
  }
}
