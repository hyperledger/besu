package org.hyperledger.besu.consensus.merge;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class TransitionUtils<SwitchingObject> {

  private final MergeContext mergeContext = MergeContext.get();
  private final SwitchingObject preMergeObject;
  private final SwitchingObject postMergeObject;

  public TransitionUtils(
      final SwitchingObject preMergeObject, final SwitchingObject postMergeObject) {
    this.preMergeObject = preMergeObject;
    this.postMergeObject = postMergeObject;
  }

  protected void dispatchConsumerAccordingToMergeState(final Consumer<SwitchingObject> consumer) {
    consumer.accept(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }

  protected <T> T dispatchFunctionAccordingToMergeState(
      final Function<SwitchingObject, T> function) {
    return function.apply(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }
}
