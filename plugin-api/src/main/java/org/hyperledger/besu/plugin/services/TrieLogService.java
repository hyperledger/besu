package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import java.util.List;

public interface TrieLogService extends BesuService {

  List<TrieLogEvent.TrieLogObserver> getObservers();

  TrieLogFactory getTrieLogFactory();

  void configureTrieLogProvider(TrieLogProvider provider);

  TrieLogProvider getTrieLogProvider();
}
