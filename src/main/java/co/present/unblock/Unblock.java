package co.present.unblock;

import com.google.apphosting.api.ApiProxy;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Detects blocking operations and logs an error when they exceed the threshold.
 *
 * @author Bob Lee (bob@present.co)
 */
public class Unblock {

  static final Logger logger = Logger.getLogger(BlockingMonitor.class.getName());

  static final int DEFAULT_DEADLINE = Integer.parseInt(System.getProperty(
      "co.present.unblock.defaultDeadline", "500"));

  private Unblock() {}

  private static boolean installed;

  /** Installs the monitor for App Engine. */
  public static void install() {
    if (installed) throw new IllegalStateException("Already installed");
    @SuppressWarnings("unchecked")
    ApiProxy.Delegate<ApiProxy.Environment> delegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(new ApiProxyMonitor(delegate));
    installed = true;
  }

  /**
   * Sets up the necessary context for monitoring, runs the given job, and logs an error if
   * we exceed the threshold for blocking calls during the job. In web apps, use
   * {@link UnblockFilter} instead.
   */
  public static void monitor(String description, Runnable job) {
    BlockingMonitor monitor = new BlockingMonitor(description);
    try {
      BlockingMonitor.localMonitor.set(monitor);
      job.run();
    } finally {
      monitor.log();
      BlockingMonitor.localMonitor.remove();
    }
  }

  /**
   * Sets the deadline, the maximum time calls can block before we log an error, for the current
   * request.
   */
  public static void setDeadline(long duration, TimeUnit unit) {
    BlockingMonitor monitor = BlockingMonitor.localMonitor.get();
    if (monitor == null) throw new IllegalStateException("Monitor is not installed.");
    monitor.deadline = unit.toMillis(duration);
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
