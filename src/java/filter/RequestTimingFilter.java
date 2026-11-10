package filter;

import dao.DBContext;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Correlates slow dynamic requests without logging query parameters or form data. */
@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class RequestTimingFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(RequestTimingFilter.class.getName());
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String id = UUID.randomUUID().toString();
        res.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
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
