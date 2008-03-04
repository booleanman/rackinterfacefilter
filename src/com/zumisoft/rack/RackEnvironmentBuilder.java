package com.zumisoft.rack;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.RubyHash;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyIO;
import org.jruby.RubyStringIO;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.io.STDIO;

/**
 * Creates a rack compatible environment hash from request
 * 
 * @author Fred McCann
 */
@SuppressWarnings("unchecked")
public class RackEnvironmentBuilder {
    private static Log log = LogFactory.getLog(RackEnvironmentBuilder.class);
    protected static final String HTTP_HEAD_PREPEPEND = "HTTP_";
    protected static final Integer[] SUPPORTED_RACK_VERSION = {new Integer(0), new Integer(1)};
    
    // Rack environment keys
    protected static final String REQUEST_METHOD = "REQUEST_METHOD";
    protected static final String REQUEST_URI = "REQUEST_URI";    
    protected static final String SCRIPT_NAME = "SCRIPT_NAME";
    protected static final String PATH_INFO = "PATH_INFO";
    protected static final String QUERY_STRING = "QUERY_STRING";
    protected static final String SERVER_NAME = "SERVER_NAME";
    protected static final String SERVER_PORT = "SERVER_PORT";
    protected static final String RACK_VERSION = "rack.version";
    protected static final String RACK_URL_SCHEME = "rack.url_scheme";
    protected static final String RACK_INPUT = "rack.input";
    protected static final String RACK_ERRORS = "rack.errors";
    protected static final String RACK_MULTITHREAD = "rack.multithread";
    protected static final String RACK_MULTIPROCESS = "rack.multiprocess";
    protected static final String RACK_RUN_ONCE = "rack.run_once";    
    protected static final String CONTENT_TYPE = "CONTENT_TYPE";
    
    /**
     * This is used to construct keys for header names
     * 
     * @param header
     * @return the key under which to store the header in the rack env
     */
    private static String httpHeaderKey(final String header) {
        return new StringBuilder(HTTP_HEAD_PREPEPEND).append(header.toUpperCase().replaceAll("-", "_")).toString();
    }
    
    /**
     * Translates an HttpServletRequest into a rack compatible hash
     * 
     * @param runtime The ruby runtime
     * @param req an HttpServletRequest
     * @return A Ruby hash according to the rack spec
     * @throws IOException
     */
    public static RubyHash buildEnvironment(Ruby runtime, HttpServletRequest req) throws IOException {
        RubyHash env = new RubyHash(runtime);
        log.debug("Creating Rack environment");        
        
        env.put(runtime.newString(REQUEST_METHOD), runtime.newString(req.getMethod()));

        // I can't see where this is required in the spec, but Merb wants it
        env.put(runtime.newString(REQUEST_URI), runtime.newString(req.getServletPath()));        
        
        if (req.getServletPath().equals("/"))
        	env.put(runtime.newString(SCRIPT_NAME), runtime.newString(""));
        else
        	env.put(runtime.newString(SCRIPT_NAME), runtime.newString(req.getServletPath()));
        	
        if (req.getPathInfo() == null)
            env.put(runtime.newString(PATH_INFO), runtime.newString(""));
        else
            env.put(runtime.newString(PATH_INFO), runtime.newString(req.getPathInfo()));
        
        if (req.getQueryString() == null)
            env.put(runtime.newString(QUERY_STRING), runtime.newString(""));
        else
            env.put(runtime.newString(QUERY_STRING), runtime.newString(req.getQueryString()));
        
        env.put(runtime.newString(SERVER_NAME), runtime.newString(req.getServerName()));
        
        String port = Integer.toString(req.getServerPort());
        env.put(runtime.newString(SERVER_PORT), runtime.newString(port));
        
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();                  
            String value = req.getHeader(name);

            if (value != null) {
                final String key = httpHeaderKey(name);
                env.put(runtime.newString(key), runtime.newString(value));
            }
        }        

        RubyArray version = RubyArray.newArray(runtime);
        version.add(SUPPORTED_RACK_VERSION[0]);
        version.add(SUPPORTED_RACK_VERSION[1]);
        env.put(runtime.newString(RACK_VERSION), version);
        
        env.put(runtime.newString(RACK_URL_SCHEME), runtime.newString(req.getScheme()));
		        
        // Read request's input stream and package it up as a StringIO object for consumption
		String body = null;
		try {
		    body = RackInterfaceUtils.readInputStream(new BufferedInputStream(req.getInputStream()));
		    
		    if (log.isDebugEnabled())
		    	log.debug("Request body: "+body);
		    
		    RubyStringIO requestBody = (RubyStringIO)RubyStringIO.createStringIOClass(runtime)
		    	.newInstance(new IRubyObject[]{runtime.newString(body.toString())}, Block.NULL_BLOCK);
			env.put(runtime.newString(RACK_INPUT), requestBody);	
			log.debug("SIZE = "+requestBody.size());
		}
		catch (Exception e) {
			final String message = "Error reading request: "+e.getMessage();
			log.error(message, e);
			throw new RuntimeException(message, e);
		}

        env.put(runtime.newString(RACK_ERRORS), new RubyIO(runtime, STDIO.ERR));        
        env.put(runtime.newString(RACK_MULTITHREAD), runtime.newBoolean(true)); // I think this is kosher. Maybe should be a setting?
        env.put(runtime.newString(RACK_MULTIPROCESS), runtime.newBoolean(false)); // I think this is kosher. Maybe should be a setting?
        env.put(runtime.newString(RACK_RUN_ONCE), runtime.newBoolean(false));

        // I don't see this in the Rack spec, but Merb certainly wants it
        env.put(runtime.newString(CONTENT_TYPE), req.getContentType());
        
        if (log.isDebugEnabled())
        	log.debug("env = " + env.inspect());

        return env;        
    }
}
