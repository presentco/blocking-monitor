package example;

import co.present.unblock.Unblock;
import com.google.apphosting.api.ApiProxy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Example {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    ApiProxy.setDelegate(new FakeDelegate());
    Unblock.install();
    ApiProxy.Delegate delegate = ApiProxy.getDelegate();
    Unblock.monitor("Example", () -> {
      for (int i = 0; i < 4; i++) {
        try {
          delegate.makeAsyncCall(null, "example", "foo", null, null).get();
          for (int ii = 0; ii < 2; ii++) {
            delegate.makeAsyncCall(null, "example", "bar", null, null).get();
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public interface Foo {
    Future<String> a();
    Future<String> b();
  }

  private static class FakeFuture implements Future<byte[]> {
    @Override public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override public boolean isCancelled() {
      return false;
    }

    @Override public boolean isDone() {
      return false;
    }

    @Override public byte[] get() throws InterruptedException, ExecutionException {
      return null;
    }

    @Override public byte[] get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      throw new UnsupportedOperationException();
    }
  }

  static class FakeDelegate implements ApiProxy.Delegate {
    @Override
    public byte[] makeSyncCall(ApiProxy.Environment environment, String s, String s1, byte[] bytes)
        throws ApiProxy.ApiProxyException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Future<byte[]> makeAsyncCall(ApiProxy.Environment environment, String s, String s1,
        byte[] bytes, ApiProxy.ApiConfig apiConfig) {
      return new FakeFuture();
    }

    @Override public void log(ApiProxy.Environment environment, ApiProxy.LogRecord logRecord) {
      throw new UnsupportedOperationException();
    }

    @Override public void flushLogs(ApiProxy.Environment environment) {
      throw new UnsupportedOperationException();
    }

    @Override public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
      throw new UnsupportedOperationException();
    }
  }
}
