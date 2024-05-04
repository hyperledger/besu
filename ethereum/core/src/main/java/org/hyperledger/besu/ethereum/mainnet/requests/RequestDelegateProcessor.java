package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

/** Processes various types of requests based on their RequestType. */
public class RequestDelegateProcessor implements RequestProcessor {
  private final ImmutableMap<RequestType, RequestProcessor> processors;

  /**
   * Constructs a RequestsProcessor with a given map of processors.
   *
   * @param processors A map associating RequestType with their corresponding RequestProcessor.
   */
  public RequestDelegateProcessor(final ImmutableMap<RequestType, RequestProcessor> processors) {
    this.processors = processors;
  }

  /**
   * Processes all requests for the available request types in the processors map.
   *
   * @param mutableWorldState The mutable world state to be used by the processors.
   * @return A list of requests.
   */
  @Override
  public Optional<List<Request>> process(final MutableWorldState mutableWorldState) {
    List<Request> requests = new ArrayList<>();
    processors.values().stream()
        .map(processor -> processor.process(mutableWorldState))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(requests::addAll);
    return Optional.of(requests);
  }
}
