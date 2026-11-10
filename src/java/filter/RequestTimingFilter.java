package filter;

import dao.DBContext;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Correlates slow dynamic requests without logging query parameters or form data. */
@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class RequestTimingFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(RequestTimingFilter.class.getName());
    // Leave room for DAOs that open another connection inside a transaction.
    private final Semaphore requests = new Semaphore(Math.max(1,
            Integer.getInteger("tourma.http.maxConcurrentRequests", 6)), true);
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/fonts/") || path.equals("/favicon.ico")) {
            chain.doFilter(request, response);
            return;
        }
        String id = UUID.randomUUID().toString();
        res.setHeader("X-Request-ID", id);
        boolean acquired;
        try { acquired = requests.tryAcquire(1000, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); acquired = false; }
        if (!acquired) {
            res.setHeader("Retry-After", "2");
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Hệ thống đang bận, vui lòng thử lại sau.");
            LOG.warning("Request " + id + " rejected: dynamic request capacity exhausted; DB " + DBContext.poolSnapshot());
            return;
        }
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            requests.release();
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (millis >= 1000 || failed) {
                LOG.warning("Request " + id + " " + req.getMethod() + " "
                        + req.getRequestURI().replace('\r', '_').replace('\n', '_')
                        + " " + millis + "ms status=" + res.getStatus() + " failed=" + failed
                        + "; DB " + DBContext.poolSnapshot());
            }
        }
    }
}
