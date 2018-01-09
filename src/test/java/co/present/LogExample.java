package co.present;

import co.present.unblock.Unblock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LogExample {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    Foo foo = Unblock.proxy(Foo.class, new FakeFoo());
    Unblock.monitor("Example", () -> {
      for (int i = 0; i < 4; i++) {
        try {
          foo.a().get();
          for (int ii = 0; ii < 2; ii++) foo.b().get();
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

  private static class FakeFuture implements Future<String> {
    @Override public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override public boolean isCancelled() {
      return false;
    }

    @Override public boolean isDone() {
      return false;
    }

    @Override public String get() throws InterruptedException, ExecutionException {
      return null;
    }

    @Override public String get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      throw new UnsupportedOperationException();
    }
  }

  private static class FakeFoo implements Foo {
    @Override public Future<String> a() {
      return new FakeFuture();
    }

    @Override public Future<String> b() {
      return new FakeFuture();
    }
  }
}
