package filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RequestTimingFilterTest {
    static HttpServletRequest request(String path) {
        return (HttpServletRequest)Proxy.newProxyInstance(RequestTimingFilterTest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)-> {
            switch(m.getName()) {
                case "getRequestURI": return path;
                case "getContextPath": return "";
                case "getMethod": return "GET";
                default: return null;
            }
        });
    }
    static HttpServletResponse response(AtomicInteger status) {
        return (HttpServletResponse)Proxy.newProxyInstance(RequestTimingFilterTest.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)-> {
            if(m.getName().equals("getStatus")) return status.get();
            if(m.getName().equals("sendError")) status.set((int)a[0]);
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("tourma.http.maxConcurrentRequests","1");
        RequestTimingFilter filter=new RequestTimingFilter();
        System.clearProperty("tourma.http.maxConcurrentRequests");
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=worker.submit(()-> {
                try {
                    filter.doFilter(request("/team-profile"),response(new AtomicInteger(200)),(r,s)-> {
                        entered.countDown();
                        try { if(!release.await(5,TimeUnit.SECONDS))throw new ServletException("Test stalled"); }
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServletException(e);}
                    });
                } catch(Exception e){throw new RuntimeException(e);}
            });
            if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("Request did not enter");
            AtomicInteger status=new AtomicInteger(200), calls=new AtomicInteger();
            filter.doFilter(request("/rolling/standings"),response(status),(r,s)->calls.incrementAndGet());
            if(status.get()!=503 || calls.get()!=0)throw new AssertionError("Overload reached the DAO");
            filter.doFilter(request("/css/design-tokens.css"),response(new AtomicInteger(200)),(r,s)->calls.incrementAndGet());
            if(calls.get()!=1)throw new AssertionError("Assets were blocked by dynamic admission");
            release.countDown();first.get(2,TimeUnit.SECONDS);
            try {
                filter.doFilter(request("/team-profile"),response(new AtomicInteger(200)),(r,s)->{throw new ServletException("Expected test failure");});
                throw new AssertionError("Failure swallowed");
            } catch(ServletException expected) { }
            filter.doFilter(request("/team-profile"),response(new AtomicInteger(200)),(r,s)->calls.incrementAndGet());
            if(calls.get()!=2)throw new AssertionError("Request permit leaked after failure");
        } finally { release.countDown();worker.shutdownNow(); }
        System.out.println("PASS: overload returns 503, assets bypass admission, failures release capacity");
    }
}
