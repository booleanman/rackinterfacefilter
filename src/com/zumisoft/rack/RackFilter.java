package com.zumisoft.rack;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A filter that intercepts all requests and sends them to a ruby web framework
 * via the rack interface
 * 
 * @author Fred McCann
 */
public class RackFilter implements Filter {
	private static Log log = LogFactory.getLog(RackFilter.class);	
    private RackRequestHandler handler = null;
    
    /**
     * Currently sets up a single instance of a RackRequestHandler and initializes it.
     * This could be changed to work with a pool of handlers if needed
     */
	public void init(FilterConfig config) throws ServletException {
		log.debug("Initializing RackFilter");
		handler = new RackRequestHandler(config);
    }

	/**
	 * Examines incoming requests. If the request matches an ignore path, 
	 * it's passed on to other filters / servlets
	 */
	public void doFilter(ServletRequest req, ServletResponse resp,
			FilterChain chain) throws IOException, ServletException {
		log.debug("Call RackFilter");      		
		if (!handler.call((HttpServletRequest)req, (HttpServletResponse)resp)) 
			chain.doFilter (req, resp);			
	}

	/**
	 * Shuts down the existing RackRequestHandler
	 */
	public void destroy() {
		log.debug("Destroying RackFilter");
		handler.shutdown();
	}
}
