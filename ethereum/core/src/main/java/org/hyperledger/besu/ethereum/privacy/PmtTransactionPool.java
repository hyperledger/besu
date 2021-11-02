package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PmtTransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Hash, PmtTransactionTracker> pmtPool;

  public PmtTransactionPool() {
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
              LOG.debug("removing " + pmtPool.containsKey(tx.getHash()));
              pmtPool.remove(tx.getHash());
            });
  }

  public Optional<Long> getMaxMatchingNonce(final String sender, final String privacyGroupId) {

    return pmtPool.values().stream()
        .filter(
            pmt ->
                pmt.getSender().equals(sender)
                    && pmt.getPrivacyGroupIdBase64().equals(privacyGroupId))
        .map(pmt -> pmt.getPrivateNonce())
        .max(Long::compare);
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final PrivateTransaction privateTx,
      final String privacyGroupId,
      final long publicNonce) {
    return addPmtTransactionTracker(
        pmtHash, privateTx.sender.toHexString(), privacyGroupId, privateTx.getNonce(), publicNonce);
  }

  public Hash addPmtTransactionTracker(
      final Hash pmtHash,
      final String sender,
      final String privacyGroupId,
      final long privateNonce,
      final long publicNonce) {

    final PmtTransactionTracker pmtTracker =
        new PmtTransactionTracker(sender, privacyGroupId, privateNonce, publicNonce);
    pmtPool.put(pmtHash, pmtTracker);
    LOG.debug("adding pmtPool tracker: pmtTracker {} pmtHash: {} ", pmtTracker, pmtHash);
    return pmtHash;
  }

  protected static class PmtTransactionTracker {
    private final String sender;
    private final String privacyGroupIdBase64;
    private final long privateNonce;
    private final long publicNonce;

    protected PmtTransactionTracker(
        final String sender,
        final String privacyGroupIdBase64,
        final long privateNonce,
        final long publicNonce) {
      this.sender = sender;
      this.privacyGroupIdBase64 = privacyGroupIdBase64;
      this.privateNonce = privateNonce;
      this.publicNonce = publicNonce;
    }

    public String getSender() {
      return sender;
    }

    public String getPrivacyGroupIdBase64() {
      return privacyGroupIdBase64;
    }

    public long getPrivateNonce() {
      return privateNonce;
    }

    public long getPublicNonce() {
      return publicNonce;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("PmtTransactionTracker ").append("{");
      sb.append("private nonce=").append(getPrivateNonce()).append(", ");
      sb.append("public nonce=").append(getPublicNonce()).append(", ");
      sb.append("sender=").append(getSender()).append(", ");
      sb.append("privacyGroupId=").append(getPrivacyGroupIdBase64()).append(", ");
      return sb.append("}").toString();
    }
  }
}
