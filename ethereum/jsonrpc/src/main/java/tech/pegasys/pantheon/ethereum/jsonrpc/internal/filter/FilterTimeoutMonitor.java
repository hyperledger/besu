package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

class FilterTimeoutMonitor {

  private final FilterRepository filterRepository;

  FilterTimeoutMonitor(final FilterRepository filterRepository) {
    this.filterRepository = filterRepository;
  }

  void checkFilters() {
    filterRepository
        .getFilters()
        .forEach(
            filter -> {
              if (filter.isExpired()) {
                filterRepository.delete(filter.getId());
              }
            });
  }
}
