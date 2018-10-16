package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilterTimeoutMonitorTest {

  @Mock private FilterRepository filterRepository;

  private FilterTimeoutMonitor timeoutMonitor;

  @Before
  public void before() {
    timeoutMonitor = new FilterTimeoutMonitor(filterRepository);
  }

  @Test
  public void expiredFilterShouldBeDeleted() {
    Filter filter = spy(new BlockFilter("foo"));
    when(filter.isExpired()).thenReturn(true);
    when(filterRepository.getFilters()).thenReturn(Lists.newArrayList(filter));

    timeoutMonitor.checkFilters();

    verify(filterRepository).getFilters();
    verify(filterRepository).delete("foo");
    verifyNoMoreInteractions(filterRepository);
  }

  @Test
  public void nonExpiredFilterShouldNotBeDeleted() {
    Filter filter = mock(Filter.class);
    when(filter.isExpired()).thenReturn(false);
    when(filterRepository.getFilters()).thenReturn(Lists.newArrayList(filter));

    timeoutMonitor.checkFilters();

    verify(filter).isExpired();
    verifyNoMoreInteractions(filter);
  }

  @Test
  public void checkEmptyFilterRepositoryDoesNothing() {
    when(filterRepository.getFilters()).thenReturn(Collections.emptyList());

    timeoutMonitor.checkFilters();

    verify(filterRepository).getFilters();
    verifyNoMoreInteractions(filterRepository);
  }
}
