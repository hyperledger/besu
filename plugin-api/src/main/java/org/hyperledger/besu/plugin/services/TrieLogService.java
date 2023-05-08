package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import java.util.List;

/**
 * A service interface for registering observers for trie log events.
 *
 * <p>Implementations must be thread-safe.
 */
public interface TrieLogService extends BesuService {

  /**
   * Provides list of observers to configure for trie log events.
   *
   * @return the list of observers to configure
   */
  List<TrieLogEvent.TrieLogObserver> getObservers();

  /**
   * Provide a TrieLogFactory implementation to use for serializing and deserializing TrieLogs.
   *
   * @return the TrieLogFactory implementation
   */
  TrieLogFactory getTrieLogFactory();

  /**
   * Configure a TrieLogProvider implementation to use for retrieving stored TrieLogs.
   *
   * @param provider the TrieLogProvider implementation
   */
  void configureTrieLogProvider(TrieLogProvider provider);

  /**
   * Retrieve the configured TrieLogProvider implementation.
   *
   * @return the TrieLogProvider implementation
   */
  TrieLogProvider getTrieLogProvider();
}
