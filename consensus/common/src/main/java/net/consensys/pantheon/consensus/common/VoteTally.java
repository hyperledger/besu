package net.consensys.pantheon.consensus.common;

import net.consensys.pantheon.ethereum.core.Address;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.collect.Maps;

/** Tracks the current list of validators and votes to add or drop validators. */
public class VoteTally implements ValidatorProvider {

  private final SortedSet<Address> currentValidators;

  private final Map<Address, Set<Address>> addVotesBySubject;
  private final Map<Address, Set<Address>> removeVotesBySubject;

  public VoteTally(final List<Address> initialValidators) {
    this(new TreeSet<>(initialValidators), new HashMap<>(), new HashMap<>());
  }

  private VoteTally(
      final Collection<Address> initialValidators,
      final Map<Address, Set<Address>> addVotesBySubject,
      final Map<Address, Set<Address>> removeVotesBySubject) {
    this.currentValidators = new TreeSet<>(initialValidators);
    this.addVotesBySubject = addVotesBySubject;
    this.removeVotesBySubject = removeVotesBySubject;
  }

  /**
   * Add a vote to the current tally. The current validator list will be updated if this vote takes
   * the tally past the required votes to approve the change.
   *
   * @param proposer the address of the validator casting the vote via block proposal
   * @param subject the validator the vote is about
   * @param voteType the type of vote, either add or drop
   */
  public void addVote(final Address proposer, final Address subject, final VoteType voteType) {
    final Set<Address> addVotesForSubject =
        addVotesBySubject.computeIfAbsent(subject, target -> new HashSet<>());
    final Set<Address> removeVotesForSubject =
        removeVotesBySubject.computeIfAbsent(subject, target -> new HashSet<>());

    if (voteType == VoteType.ADD) {
      addVotesForSubject.add(proposer);
      removeVotesForSubject.remove(proposer);
    } else {
      removeVotesForSubject.add(proposer);
      addVotesForSubject.remove(proposer);
    }

    final int validatorLimit = validatorLimit();
    if (addVotesForSubject.size() >= validatorLimit) {
      currentValidators.add(subject);
      discardOutstandingVotesFor(subject);
    }
    if (removeVotesForSubject.size() >= validatorLimit) {
      currentValidators.remove(subject);
      discardOutstandingVotesFor(subject);
      addVotesBySubject.values().forEach(votes -> votes.remove(subject));
      removeVotesBySubject.values().forEach(votes -> votes.remove(subject));
    }
  }

  private void discardOutstandingVotesFor(final Address subject) {
    addVotesBySubject.remove(subject);
    removeVotesBySubject.remove(subject);
  }

  public Set<Address> getOutstandingAddVotesFor(final Address subject) {
    return Optional.ofNullable(addVotesBySubject.get(subject)).orElse(Collections.emptySet());
  }

  public Set<Address> getOutstandingRemoveVotesFor(final Address subject) {
    return Optional.ofNullable(removeVotesBySubject.get(subject)).orElse(Collections.emptySet());
  }

  private int validatorLimit() {
    return (currentValidators.size() / 2) + 1;
  }

  /**
   * Reset the outstanding vote tallies as required at each epoch. The current validator list is
   * unaffected.
   */
  public void discardOutstandingVotes() {
    addVotesBySubject.clear();
  }

  @Override
  public Collection<Address> getCurrentValidators() {
    return currentValidators;
  }

  public VoteTally copy() {
    final Map<Address, Set<Address>> addVotesBySubject = Maps.newHashMap();
    final Map<Address, Set<Address>> removeVotesBySubject = Maps.newHashMap();

    this.addVotesBySubject.forEach(
        (key, value) -> addVotesBySubject.put(key, new TreeSet<>(value)));
    this.removeVotesBySubject.forEach(
        (key, value) -> removeVotesBySubject.put(key, new TreeSet<>(value)));

    return new VoteTally(
        new TreeSet<>(this.currentValidators), addVotesBySubject, removeVotesBySubject);
  }
}
