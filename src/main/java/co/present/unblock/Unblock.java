package co.present.unblock;

import com.google.common.base.Preconditions;
import java.lang.reflect.Proxy;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Detects blocking operations and logs an error when they exceed the threshold.
 *
 * @author Bob Lee (bob@present.co)
 */
public class Unblock {

  static final Logger logger = Logger.getLogger(BlockingMonitor.class.getName());

  static final int ERROR_THRESHOLD = Integer.parseInt(System.getProperty(
      Unblock.class.getName() + ".ERROR_THRESHOLD", "10"));

  private Unblock() {}

  /**
   * Proxies an interface and intercepts methods that return {@link Future}. If your code calls
   * {@link Future#get()} on one of the returned futures and the value isn't ready yet, Unblock
   * registers a blocking operation. If the number of blocking operations exceeds the threshold,
   * Unblock logs an error at the end of the request.
   */
  public static <T> T proxy(Class<T> type, T service) {
    Preconditions.checkArgument(type.isInterface());
    return type.cast(Proxy.newProxyInstance(
        BlockingMonitor.class.getClassLoader(),
        new Class[] { type },
        new BlockingMonitor.MonitoringInvocationHandler(service)
    ));
  }

  /**
   * Sets up the necessary context for monitoring, runs the given job, and logs an error if
   * we exceed the threshold for blocking calls during the job. In web apps, use
   * {@link UnblockFilter} instead.
   */
  public static void monitor(String description, Runnable job) {
    checkNotNull(description, "description");
    checkNotNull(job, "job");
    BlockingMonitor monitor = new BlockingMonitor(description);
    try {
      BlockingMonitor.localMonitor.set(monitor);
      job.run();
    } finally {
      monitor.log();
      BlockingMonitor.localMonitor.remove();
    }
  }

  /** Don't count blocking calls during the given job. */
  public static void disableDuring(Runnable job) {
    disableDuring(() -> {
      job.run();
      return null;
    });
  }

  /** Don't count blocking calls during the given job. */
  public static <T> T disableDuring(Supplier<T> job) {
    logger.info("Ignoring blocking operations.");
    BlockingMonitor monitor = BlockingMonitor.localMonitor.get();
    if (monitor.disabled) return job.get();
    monitor.disabled = true;
    try {
      return job.get();
    } finally {
      monitor.disabled = false;
    }
  }
}
