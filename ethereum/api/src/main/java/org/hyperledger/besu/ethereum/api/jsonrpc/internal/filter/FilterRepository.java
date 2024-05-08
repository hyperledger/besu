/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The type Filter repository. */
public class FilterRepository {

  private final Map<String, Filter> filters = new ConcurrentHashMap<>();

  /** Instantiates a new Filter repository. */
  public FilterRepository() {}

  /**
   * Gets filters.
   *
   * @return the filters
   */
  Collection<Filter> getFilters() {
    return new ArrayList<>(filters.values());
  }

  /**
   * Gets filters of type.
   *
   * @param <T> the type parameter
   * @param filterClass the filter class
   * @return the filters of type
   */
  <T extends Filter> Collection<T> getFiltersOfType(final Class<T> filterClass) {
    return filters.values().stream()
        .flatMap(f -> getIfTypeMatches(f, filterClass).map(Stream::of).orElseGet(Stream::empty))
        .collect(Collectors.toList());
  }

  /**
   * Gets filter.
   *
   * @param <T> the type parameter
   * @param filterId the filter id
   * @param filterClass the filter class
   * @return the filter
   */
  <T extends Filter> Optional<T> getFilter(final String filterId, final Class<T> filterClass) {
    final Filter filter = filters.get(filterId);
    return getIfTypeMatches(filter, filterClass);
  }

  @SuppressWarnings("unchecked")
  private <T extends Filter> Optional<T> getIfTypeMatches(
      final Filter filter, final Class<T> filterClass) {
    if (filter == null) {
      return Optional.empty();
    }

    if (!filterClass.isAssignableFrom(filter.getClass())) {
      return Optional.empty();
    }

    return Optional.of((T) filter);
  }

  /**
   * Exists boolean.
   *
   * @param id the id
   * @return the boolean
   */
  boolean exists(final String id) {
    return filters.containsKey(id);
  }

  /**
   * Save.
   *
   * @param filter the filter
   */
  void save(final Filter filter) {
    if (filter == null) {
      throw new IllegalArgumentException("Can't save null filter");
    }

    if (exists(filter.getId())) {
      throw new IllegalArgumentException(
          String.format("Filter with id %s already exists", filter.getId()));
    }

    filters.put(filter.getId(), filter);
  }

  /**
   * Delete.
   *
   * @param id the id
   */
  void delete(final String id) {
    filters.remove(id);
  }

  /** Delete all. */
  void deleteAll() {
    filters.clear();
  }
}
