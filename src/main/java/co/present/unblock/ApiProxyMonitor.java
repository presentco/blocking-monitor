package co.present.unblock;

import com.google.apphosting.api.ApiProxy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class ApiProxyMonitor implements ApiProxy.Delegate<ApiProxy.Environment> {

  private final ApiProxy.Delegate<ApiProxy.Environment> delegate;

  ApiProxyMonitor(ApiProxy.Delegate<ApiProxy.Environment> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Future<byte[]> makeAsyncCall(ApiProxy.Environment environment, String packageName,
      String methodName, byte[] bytes, ApiProxy.ApiConfig apiConfig) {
    return new BlockingMonitor.MonitoringFuture<>(
        delegate.makeAsyncCall(environment, packageName, methodName, bytes, apiConfig),
        packageName + "." + methodName);
  }

  @Override
  public byte[] makeSyncCall(ApiProxy.Environment environment, String s, String s1, byte[] bytes)
      throws ApiProxy.ApiProxyException {
    try {
      return makeAsyncCall(environment, s, s1, bytes, new ApiProxy.ApiConfig()).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ApiProxy.ApiProxyException(e.getMessage(), e);
    }
  }

  @Override public void log(ApiProxy.Environment environment, ApiProxy.LogRecord logRecord) {
    delegate.log(environment, logRecord);
  }

  @Override public void flushLogs(ApiProxy.Environment environment) {
    delegate.flushLogs(environment);
  }

  @Override public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
    return delegate.getRequestThreads(environment);
  }
}