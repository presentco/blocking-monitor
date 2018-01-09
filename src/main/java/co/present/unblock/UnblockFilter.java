package co.present.unblock;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Monitors a request and logs an error if we exceed the threshold for blocking calls.
 *
 * @author Bob Lee (bob@present.co)
 */
public class UnblockFilter implements javax.servlet.Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    BlockingMonitor monitor = new BlockingMonitor(httpRequest.getRequestURI());
    try {
      BlockingMonitor.localMonitor.set(monitor);
      chain.doFilter(request, response);
    } finally {
      monitor.log();
      BlockingMonitor.localMonitor.remove();
    }
  }

  @Override public void init(FilterConfig filterConfig) throws ServletException {}
  @Override public void destroy() {}
}
