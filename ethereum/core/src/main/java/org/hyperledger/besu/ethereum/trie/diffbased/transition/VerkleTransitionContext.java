package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.ethereum.trie.diffbased.transition.storage.VerkleTransitionWorldStateKeyValueStorage;
import org.hyperledger.besu.util.Subscribers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.FINALIZED;
import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.PRE_TRANSITION;
import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.STARTED;

/**
 * TODO: this should be a singleton managed by dagger
 */
public class VerkleTransitionContext {
  enum TransitionStatus {
    PRE_TRANSITION,
    STARTED,
    FINALIZED
  }

  final Subscribers<VerkleTransitionSubscriber> subscribers = Subscribers.create();
  final static Logger LOG = LoggerFactory.getLogger(VerkleTransitionWorldStateKeyValueStorage.class);

  // start in PRE_TRANSITION, rely on startup checks to correctly set the current state
  final AtomicReference<TransitionStatus> transitionStatus = new AtomicReference<>(PRE_TRANSITION);

  public long subscribe(VerkleTransitionSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public boolean isMigrating() {
    return transitionStatus.get() == TransitionStatus.STARTED;
  };

  public boolean isFinalized() {
    return transitionStatus.get() == FINALIZED;
  };


  public synchronized boolean startTransition() {
    if (transitionStatus.get() == PRE_TRANSITION) {
      transitionStatus.set(STARTED);
      subscribers.forEach(VerkleTransitionSubscriber::onTransitionStarted);
      //TODO: some cool ascii art for verkle transition started
      LOG.info("Started verkle transition");
      return true;
    } else {
      LOG.warn("Ignoring request to start transition already started or in progress");
      return false;
    }
  }

  public synchronized boolean revertTransition() {
    if (transitionStatus.get() != FINALIZED) {
      transitionStatus.set(FINALIZED);
      subscribers.forEach(VerkleTransitionSubscriber::onTransitionReverted);
      LOG.info("Reverting verkle transition");
      return true;
    } else {
      LOG.error("Attempt to revert a finalized verkle transition");
      return false;
    }
  }

  /**
   * Mark the transition as finalized, alert subscribers
   */
  public void finalizeTransition() {
    if (transitionStatus.getAndSet(FINALIZED) != FINALIZED) {
      subscribers.forEach(VerkleTransitionSubscriber::onTransitionFinalized);
      // TODO: put some fun ascii art in the logs
      LOG.info("Verkle transition complete");
    }
  }

  public interface VerkleTransitionSubscriber {
    void onTransitionStarted();
    void onTransitionReverted();
    void onTransitionFinalized();
  }
}
