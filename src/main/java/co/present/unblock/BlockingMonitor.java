package co.present.unblock;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.ForwardingFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  private static final int WARN_THRESHOLD = Unblock.ERROR_THRESHOLD * 2 / 3;

  private static final int MAX_STACK_DEPTH = 10;

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
      StringBuilder builder = new StringBuilder()
          .append(totalBlocks)
          .append(" of ")
          .append(totalCalls)
          .append(" (")
          .append(100 * totalBlocks / totalCalls)
          .append("%) async calls blocked in '")
          .append(this.description)
          .append("'.");
      if (this.ignored > 0) {
        builder.append(" Ignored ").append(ignored).append(" blocking calls.");
      }
      int blockCount = blockedCalls.size();
      Level level = blockCount > Unblock.ERROR_THRESHOLD ? Level.SEVERE
          : blockCount > WARN_THRESHOLD ? Level.WARNING : Level.INFO;
      // If FINE logging is enabled, we always include the stacktrace.
      if (logger.isLoggable(Level.FINE) || level.intValue() > Level.INFO.intValue()) {
        builder.append('\n');
        // De-duplicate stacktraces and sort them by frequency.
        Multiset<BlockedCall> sorted = Multisets.copyHighestCountFirst(
            HashMultiset.create(blockedCalls));
        for (Multiset.Entry<BlockedCall> entry : sorted.entrySet()) {
          int count = entry.getCount();
          BlockedCall call = entry.getElement();
          builder.append("Result of ")
              .append(call.method)
              .append(" blocked ")
              .append(count)
              .append(count == 1 ? " time" : " times")
              .append('\n');
          for (String element : call.stack()) {
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

  private static class BlockedCall {

    private final String method;
    private final Throwable throwable;

    private BlockedCall(String method, Throwable throwable) {
      this.method = method;
      this.throwable = throwable;
    }

    private List<String> stack;
    private int hash;

    private List<String> stack() {
      if (this.stack == null) {
        this.stack = truncate(this.throwable.getStackTrace());
        this.hash = this.method.hashCode() * 31 + this.stack.hashCode();
      }
      return this.stack;
    }

    @Override public int hashCode() {
      stack(); // computes hash
      return this.hash;
    }

    @Override public boolean equals(Object o) {
      BlockedCall other = (BlockedCall) o;
      List<String> thisStack = this.stack();
      List<String> otherStack = other.stack();
      return this.method.equals(other.method) && this.hash == other.hash
          && thisStack.equals(otherStack);
    }
  }

  /** Truncates a stack trace. */
  private static List<String> truncate(StackTraceElement[] full) {
    int skip = 0;
    while (skip < full.length) {
      String clazz = full[skip].getClassName();
      if (clazz.startsWith("co.present.unblock")
          || clazz.startsWith("com.google")
          || clazz.startsWith("com.googlecode.objectify")) {
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

  static class MonitoringFuture<T> extends ForwardingFuture<T> {

    private final Future<T> delegate;
    private final String method;

    MonitoringFuture(Future<T> delegate, String method) {
      this.delegate = delegate;
      this.method = method;
    }

    @Override protected Future<T> delegate() {
      return this.delegate;
    }

    @Override public T get() throws InterruptedException, ExecutionException {
      checkDone();
      return super.get();
    }

    @Override public T get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException, TimeoutException {
      checkDone();
      return super.get(timeout, unit);
    }

    private void checkDone() {
      BlockingMonitor monitor = localMonitor.get();
      if (monitor == null) return;
      monitor.totalCalls++;
      if (!isDone()) {
        if (monitor.disabled) {
          monitor.ignored++;
        } else {
          monitor.blockedCalls.add(new BlockedCall(method, new Throwable()));
        }
      }
    }
  }
}

