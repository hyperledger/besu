package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.FINALIZED;
import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.PRE_TRANSITION;
import static org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext.TransitionStatus.STARTED;

import org.hyperledger.besu.ethereum.trie.diffbased.transition.storage.VerkleTransitionWorldStateKeyValueStorage;
import org.hyperledger.besu.util.Subscribers;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkleTransitionContext {
  enum TransitionStatus {
    PRE_TRANSITION,
    STARTED,
    FINALIZED
  }

  final Subscribers<VerkleTransitionSubscriber> subscribers = Subscribers.create();
  private static final Logger LOG =
      LoggerFactory.getLogger(VerkleTransitionWorldStateKeyValueStorage.class);

  // start in PRE_TRANSITION, rely on startup checks to correctly set the current state
  final AtomicReference<TransitionStatus> transitionStatus = new AtomicReference<>(PRE_TRANSITION);
  final AtomicLong latestTimestamp = new AtomicLong(System.currentTimeMillis());



// TODO: consider construction when implementing verkle batch conversions

//  final long transitionStartTime;
//
//  private static AtomicReference<VerkleTransitionContext> singleton = new AtomicReference<>();
//
//  public synchronized static VerkleTransitionContext {
//
//  }
//
//  private VerkleTransitionContext(long transitionStartTime) {
//    this.transitionStartTime = transitionStartTime;
//  }

  public long subscribe(VerkleTransitionSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public boolean isBeforeTransition() {
    return transitionStatus.get() == PRE_TRANSITION;
  }

  public boolean isMigrating() {
    return transitionStatus.get() == STARTED;
  }
  ;

  public boolean isFinalized() {
    return transitionStatus.get() == FINALIZED;
  }

  public boolean  markTransition(long blockTimestamp) {
    if (transitionStatus.get() == PRE_TRANSITION) {
      transitionStatus.set(STARTED);
      subscribers.forEach(VerkleTransitionSubscriber::onTransitionStarted);
      // TODO: some cool ascii art for verkle transition started
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

  /** Mark the transition as finalized, alert subscribers */
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
