package controller;
import java.util.*;
public class PointConfigRoutingCheck {
    public static void main(String[] args) {
        Map<String,String> expected=Map.of(
                "SINGLE_ELIMINATION","single-elimination.jsp",
                "DOUBLE_ELIMINATION","double-elimination.jsp",
                "ROUND_ROBIN","round-robin.jsp",
                "GROUP_STAGE","group-stage.jsp",
                "GSL","gsl.jsp",
                "SWISS_LITE","swiss-stage.jsp",
                "SWISS","swiss-stage.jsp");
        expected.forEach((format,page)-> {
            if(!page.equals(RollingPointConfigServlet.stagePage(format)))throw new AssertionError(format);
        });
        if(!"single-elimination.jsp".equals(RollingPointConfigServlet.stagePage(" single_elimination ")))throw new AssertionError("Normalization");
        try { RollingPointConfigServlet.stagePage("UNKNOWN"); throw new AssertionError("Unknown format defaulted silently"); }
        catch(IllegalArgumentException expectedFailure) { }
        System.out.println("PASS: all persisted format routes and no silent GS fallback");
    }
}
