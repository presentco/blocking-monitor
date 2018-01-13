package co.present.unblock;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static co.present.unblock.Unblock.logger;

/**
 * Monitors asynchronous services and logs an error if too many results block (indicating
 * your code is making too many round trips across the network). Works by intercepting
 * the futures returned by the service and counting how many block when accessed.
 *
 * @author Bob Lee (bob@present.co)
 */
class BlockingMonitor {

  /** Maximum number of stack trace elements to consider. */
  private static final int MAX_STACK_DEPTH = 10;

  /** We only print stacktraces when the total duration exceeds this minimum. */
  private static final int MINIMUM_DURATION = 50;

  /** Maximum time in ms calls can block before we log an error. */
  long deadline = Unblock.DEFAULT_DEADLINE;

  /** Describes the context of this monitor. */
  private final String description;

  /** Total number of asynchronous calls. */
  private int totalCalls;

  /** Which method calls blocked. */
  private List<BlockedCall> blockedCalls = new ArrayList<>();

  /** Blocked calls that were ignored due to the monitor being disabled. */
  private int ignored;

  /** If true, blocking calls won't be counted against the threshold. */
  boolean disabled;

  BlockingMonitor(String description) {
    this.description = description;
  }

  /** Logs results of monitoring a request. */
  void log() {
    if (totalCalls == 0) {
      logger.fine("No asynchronous calls were intercepted.");
    } else {
      int totalBlocks = blockedCalls.size();
      long totalDuration = blockedCalls.stream().mapToLong(BlockedCall::duration).sum();
      StringBuilder builder = new StringBuilder()
          .append(totalBlocks)
          .append(" of ")
          .append(totalCalls)
          .append(" (")
          .append(100 * totalBlocks / totalCalls)
          .append("%) async calls blocked for ")
          .append(totalDuration)
          .append("ms total during '")
          .append(this.description)
          .append("'.");
      if (this.ignored > 0) {
        builder.append(" Ignored ").append(ignored).append(" blocking calls.");
      }
      Level level = totalDuration > this.deadline ? Level.WARNING : Level.INFO;
      // If FINE logging is enabled, we print all stacktraces.
      boolean debug = logger.isLoggable(Level.FINE);
      if (debug || level.intValue() > Level.INFO.intValue()) {
        builder.append('\n');

        // De-duplicate stacktraces. Can't use Multimap here—it hashes values, too.
        Map<BlockedCall, List<BlockedCall>> indexed = new HashMap<>();
        for (BlockedCall call : blockedCalls) {
          List<BlockedCall> all = indexed.computeIfAbsent(call, k -> new ArrayList<>());
          all.add(call);
        }

        // Count duplicate calls and sum durations. Store results in the key call.
        for (Map.Entry<BlockedCall, List<BlockedCall>> entry : indexed.entrySet()) {
          BlockedCall key = entry.getKey();
          Collection<BlockedCall> all = entry.getValue();
          key.count = all.size();
          key.duration = all.stream().mapToLong(BlockedCall::duration).sum();
          all.clear(); // Free duplicate calls for GC.
        }

        // Sort key calls by duration.
        List<BlockedCall> sorted = indexed.keySet().stream()
            .filter(call -> debug || call.duration > MINIMUM_DURATION)
            .sorted((a, b) -> Long.compare(b.duration(), a.duration()))
            .collect(Collectors.toList());

        for (BlockedCall call : sorted) {
          builder.append("Result of ")
              .append(call.method)
              .append(" blocked ")
              .append(call.count)
              .append(call.count == 1 ? " time" : " times")
              .append(" for ")
              .append(call.duration)
              .append("ms total")
              .append('\n');
          for (String element : call.stack) {
            builder.append("\tat ").append(element).append('\n');
          }
        }
      } else {
        if (!blockedCalls.isEmpty()) {
          builder.append(" Enable FINE logging level to see stacktraces.");
        }
      }
      LogRecord log = new LogRecord(level, builder.toString());
      log.setSourceClassName(Unblock.class.getName());
      logger.log(log);
    }
  }

  static final ThreadLocal<BlockingMonitor> localMonitor = new ThreadLocal<>();

  private static class BlockedCall implements Closeable {

    private final String method;
    private final List<String> stack;
    private final int hash;

    /** Duration and frequency of the call[s]. Ignored by equals and hashcode */
    private final long start = System.currentTimeMillis();
    private long duration;
    private int count;

    private BlockedCall(String method, Throwable throwable) {
      this.method = method;

      // Go ahead and realize the stack here. We're blocked anyway.
      this.stack = truncate(throwable.getStackTrace());
      this.hash = this.method.hashCode() * 31 + this.stack.hashCode();
    }

    private long duration() {
      return this.duration;
    }

    @Override public void close() {
      this.duration = System.currentTimeMillis() - start;
    }

    @Override public int hashCode() {
      return this.hash;
    }

    @Override public boolean equals(Object o) {
      BlockedCall other = (BlockedCall) o;
      return this.method.equals(other.method) && this.hash == other.hash
          && this.stack.equals(other.stack);
    }
  }

  /** Truncates a stack trace. */
  private static List<String> truncate(StackTraceElement[] full) {
    int skip = 0;
    while (skip < full.length) {
      String clazz = full[skip].getClassName();
      if (clazz.startsWith("co.present.unblock.")
          || clazz.startsWith("sun.")
          || clazz.startsWith("com.sun.")
          || clazz.startsWith("com.google.")
          || clazz.startsWith("com.googlecode.objectify.")) {
        skip++;
      } else {
        break;
      }
    }
    return Arrays.stream(full)
        .skip(skip)
        .map(Object::toString)
        .limit(MAX_STACK_DEPTH)
        .collect(Collectors.toList());
  }

  static class MonitoringFuture<T> implements Future<T> {

    private final Future<T> delegate;
    private final String method;

    MonitoringFuture(Future<T> delegate, String method) {
      this.delegate = delegate;
      this.method = method;
    }

    @Override public T get() throws InterruptedException, ExecutionException {
      try (BlockedCall call = checkDone()) {
        return delegate.get();
      }
    }

    @Override public T get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException, TimeoutException {
      try (BlockedCall call = checkDone()) {
        return delegate.get(timeout, unit);
      }
    }

    private BlockedCall checkDone() {
      BlockingMonitor monitor = localMonitor.get();
      if (monitor == null) return null;
      monitor.totalCalls++;
      if (!isDone()) {
        if (monitor.disabled) {
          monitor.ignored++;
        } else {
          BlockedCall call = new BlockedCall(method, new Throwable());
          monitor.blockedCalls.add(call);
          return call;
        }
      }
      return null;
    }

    @Override public boolean cancel(boolean mayInterruptIfRunning) {
      return delegate.cancel(mayInterruptIfRunning);
    }

    @Override public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override public boolean isDone() {
      return delegate.isDone();
    }
  }
}

