package com.zumisoft.rack;

import java.io.BufferedInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Creates a rack adapter for a specific ruby web framework
 * 
 * @author Fred McCann
 */
public class RackAdapterBuilder {
    private static Log log = LogFactory.getLog(RackAdapterBuilder.class);
    
	/**
	 * Runs bootstrap code for specified framework and returns a live rack adaptor
	 * 
	 * @param runtime The ruby runtime
	 * @param name The name of the web framework (case insensitive)
	 * @return The ruby object that acts as the  rack interface to the running webapp 
	 */
	public static IRubyObject getAdaptorForFramework(Ruby runtime, String name) {
		log.debug("Find bootstrap for framework: "+name);
	    
		String bootstrap = null;
		try {
		    bootstrap = RackInterfaceUtils.readInputStream(new BufferedInputStream(
				RackAdapterBuilder.class.getClassLoader().getResourceAsStream(
					"com/zumisoft/rack/bootstrap/"+name.toLowerCase()+".rb")));
		}		
		catch (Exception e) {
			final String message = "Can't find adaptor for framework: " + name;
			log.fatal(message, e);
			throw new RuntimeException(message, e);
		}
		
		log.debug("Bootstrap:\n"+bootstrap.toString());
		return runtime.evalScriptlet(bootstrap.toString());
	}
}
