package filter;

import dao.DBContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Do not leave idle database sessions behind when Tourma is redeployed. */
@WebListener
public class DatabaseLifecycleListener implements ServletContextListener {
    @Override public void contextDestroyed(ServletContextEvent event) {
        DBContext.closePool();
    }
}
