package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class LogsWrapper implements org.hyperledger.besu.plugin.data.Log {

  final Log delegate;

  public LogsWrapper(final Log delegate) {
    this.delegate = delegate;
  }

  @Override
  public Address getLogger() {
    return delegate.getLogger();
  }

  @Override
  public List<? extends Bytes32> getTopics() {
    return delegate.getTopics();
  }

  @Override
  public Bytes getData() {
    return delegate.getData();
  }
}
