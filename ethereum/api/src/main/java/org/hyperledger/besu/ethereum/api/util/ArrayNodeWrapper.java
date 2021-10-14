package org.hyperledger.besu.ethereum.api.util;

import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ArrayNodeWrapper {

  private final ArrayNode arrayNode;
  private final Optional<Integer> maybeAfter;
  private final Optional<Integer> maybeCount;
  private int currentOffset;

  public ArrayNodeWrapper(final ArrayNode arrayNode) {
    this(arrayNode, Optional.empty(), Optional.empty());
  }

  public ArrayNodeWrapper(
      final ArrayNode arrayNode,
      final Optional<Integer> maybeAfter,
      final Optional<Integer> maybeCount) {
    this.arrayNode = arrayNode;
    currentOffset = 0;
    this.maybeAfter = maybeAfter;
    this.maybeCount = maybeCount;
  }

  public void addPOJO(final Object object) {
    final boolean isValidOffset = maybeAfter.map(after -> currentOffset >= after).orElse(true);
    final boolean isValidSize = maybeCount.map(count -> count > arrayNode.size()).orElse(true);
    if (isValidOffset && isValidSize) {
      arrayNode.addPOJO(object);
    }
    currentOffset++;
  }

  public void addAll(final ArrayNodeWrapper wrapper) {
    final Iterator<JsonNode> elements = wrapper.arrayNode.elements();
    while (!isFull() && elements.hasNext()) {
      addPOJO(elements.next());
    }
  }

  public boolean isFull() {
    return maybeCount.map(count -> count <= arrayNode.size()).orElse(false);
  }

  public ArrayNode getArrayNode() {
    return arrayNode;
  }
}
