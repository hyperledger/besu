package tech.pegasys.pantheon.consensus.ibft;

/** Stateful evaluator for ibft events */
public class IbftStateMachine {

  /**
   * Attempt to consume the event and update the maintained state
   *
   * @param event the external action that has occurred
   * @param roundTimer timer that will fire expiry events that are expected to be received back into
   *     this machine
   * @return whether this event was consumed or requires reprocessing later once the state machine
   *     catches up
   */
  public boolean processEvent(final IbftEvent event, final RoundTimer roundTimer) {
    // TODO: don't just discard the event, do some logic
    return true;
  }
}
