package org.hyperic.hq.plugin.sunglassfish;

import java.util.ArrayList;
import java.util.List;

import org.hyperic.hq.product.PluginException;
import org.hyperic.util.config.ConfigResponse;

public class SunGlassfishNodeAgentServerDetector 
	extends SunGlassfishServerDetector {
	
	/**
	 * Overwrite to return empty services, since
	 * MxServerDetector doesn't obey
	 * HAS_BUILTIN_SERVICES=false setting.
	 */
	protected List discoverServices(ConfigResponse serverConfig) 
		throws PluginException {
		return new ArrayList();
	}
}