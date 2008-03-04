package com.zumisoft.rack;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;
import static org.jruby.util.KCode.UTF8;

/**
 * Contains an embedded ruby interpreter with a ruby web framework loaded.
 * Requests are translated into Rack compatible hashes and are passes to the framework
 * for processing.
 * 
 * @author Fred McCann
 */
@SuppressWarnings("unchecked")
public class RackRequestHandler {
    private static Log log = LogFactory.getLog(RackRequestHandler.class);
    private static final String RACK_METHOD = "call";
    private static final String PS = File.separator;
    private static final String RUBY_VERSION = "1.8";
    private Ruby runtime = null;
    private IRubyObject rackAdapter = null;
    private final String APP_HOME;
    private final String JRUBY_HOME;
    private String[] ignorePaths = new String[0];
    
    /**
     * Create a new request handler
     * 
     * <p>Responds to the mandatory filter init-params:</p>
     * <ul>
     * <li>framework: The name of the ruby framework to use (only support Merb at the moment, though ashould be able to add others I hope)/li>
     *	</ul>
     * <p>Responds to the optional filter init-params:</p>
     * <ul>
     * <li>APP_HOME: The home directory of the reby web app</li>
     * <li>JRUBY_HOME: Home directory of JRuby installtion</li>
     * <li>ignorePaths: Paths from which serve as static files or to pass processing to other servlets. Defaults to /stylesheets, /images, /favicon.ico, and /javascript</li>
     * </ul>
     */
    public RackRequestHandler(FilterConfig config) {
        log.info("Creating new RubyRequestHandler instance");
        
        // Configure ignore paths
        String pathCfg = config.getInitParameter("ignorePaths");
        if (pathCfg != null) {
            this.ignorePaths = pathCfg.split("\\s+");
        }
        else {
        	ignorePaths = new String[]{"/images", "/stylesheets", "/javascript", "/favicon.ico"};
        }
        
        if (log.isInfoEnabled())
        	for (int x=0; x<ignorePaths.length; x++)
        		log.info(ignorePaths[x] + " is configured as an ignore path.");
         
        // If jruby home is not sepcified for us, try to find it in the environment
        if (config.getInitParameter("JRUBY_HOME") != null)
            JRUBY_HOME = config.getInitParameter("JRUBY_HOME");
        else
            JRUBY_HOME = System.getenv("JRUBY_HOME");
        
        if (JRUBY_HOME == null) {
            final String message = "JRUBY_HOME is not set. This must be " +
                "provided by the envoironment or set as as " +
                "init-parameter in web.inf";
            log.fatal(message);
            throw new RuntimeException(message);
        }
        
        log.info("Using JRUBY_HOME = "+JRUBY_HOME);

        // Try to find the root directory of the ruby web app. If not specified, assume that it
        // is the parent directory of the public root
        if (config.getInitParameter("APP_HOME") != null)
        	APP_HOME = config.getInitParameter("APP_HOME");
        else {
        	File publicRoot = new File(config.getServletContext().getRealPath(PS));
        	APP_HOME =  publicRoot.getParent();
        }
        log.info("Using APP_HOME = "+APP_HOME);
        
        // JRuby Paths
        List<String> loadPath = new ArrayList<String>();        
        loadPath.add(JRUBY_HOME+PS+"lib"+PS+"ruby"+PS+"site_ruby"+PS+RUBY_VERSION);        
        loadPath.add(JRUBY_HOME+PS+"lib"+PS+"ruby"+PS+"site_ruby");        
        loadPath.add(JRUBY_HOME+PS+"lib"+PS+"ruby"+PS+RUBY_VERSION);        
        loadPath.add(JRUBY_HOME+PS+"lib"+PS+"ruby"+PS+RUBY_VERSION+PS+"java");                      
        
        // Set up Ruby Runtime
        this.runtime = JavaEmbedUtils.initialize(loadPath);
        this.runtime.setKCode(UTF8);
        this.runtime.setJRubyHome(JRUBY_HOME);
        this.runtime.setCurrentDirectory(APP_HOME);

        // Instantiate rack adapter
        try {
        	final String framework = config.getInitParameter("framework");
        	rackAdapter = RackAdapterBuilder.getAdaptorForFramework(runtime, framework);
            log.debug("Loaded "+framework+" rack adapter: " + rackAdapter.inspect());
        }
        catch (Exception e) {
            String message = "Can't load rack adapter.";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }              
    }

    /**
     * Handle a request
     * 
     * @param req
     * @param resp
     * @throws java.io.IOException
     * @return boolean false if url is matched by an ignore path
     */
     public boolean call(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // see if this request is to a static path
        String url = req.getServletPath();
        log.debug("URL = "+url);
        for (int x=0; x<ignorePaths.length; x++) {
            if (url.startsWith(ignorePaths[x])) {
                log.debug(url + " matches ignore path " + ignorePaths[x]);
                return false;
            }
        }

        // Create Rack env parameter
        RubyHash env = RackEnvironmentBuilder.buildEnvironment(runtime, req);
        
        // Call Rack adapter                       
        RubyArray responseArray = null;
        try {
            responseArray = (RubyArray)JavaEmbedUtils.invokeMethod(
                runtime, rackAdapter, RACK_METHOD, new IRubyObject[]{env}, Object.class);  
            log.debug("Response from adapter: " + responseArray.inspect());
        }
        catch (Exception e) {
            String message = "There was an error calling the rack adapter.";
            log.error(message, e);
            throw new RuntimeException(message, e);            
        }
                      
        // Write Response
        Integer responseStatus = Integer.valueOf(responseArray.get(0).toString());
        RubyHash responseHeaders = (RubyHash) responseArray.get(1);
        Object responseBody = (Object) responseArray.get(2);
        
        resp.setStatus(responseStatus);

        Iterator<String> i = responseHeaders.keys().iterator();
        while (i.hasNext()) {
            String header = i.next();
            String value = (String) responseHeaders.get(header);
            resp.setHeader(header, value);
        }

        PrintWriter bodyWriter = resp.getWriter();
        log.debug("Response is of class "+responseBody.getClass().getCanonicalName());                            
                
        // Right now this plays well with strings and not much else. I've seen some <File> objects come along.
        // They will likely need to be dealt with
        if (responseBody instanceof Object[]) {
            Object[] arr = (Object[])responseBody;
            for (int x = 0; x < arr.length; x++)
                bodyWriter.write(arr[x].toString());
        }
        else {
            bodyWriter.write(responseBody.toString());            
        }
               
        return true;
    }
    
    /**
     * Call this before disposing of the handler to properly shutdown the ruby runtime
     */
    public void shutdown() {
        log.info("Shutting down request handler.");
        runtime.tearDown();        
    }
}
