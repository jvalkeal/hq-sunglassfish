package org.hyperic.hq.plugin.sunglassfish;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.hyperic.hq.product.LogFileTrackPlugin;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerControlPlugin;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.hq.product.jmx.MxServerDetector;
import org.hyperic.util.config.ConfigResponse;

/**
 * Most of the services are discovered using jmx, but in
 * this class we overwrite some server discovery functions
 * to identify correct instances and server types.
 *
 */
public class SunGlassfishServerDetector
	extends MxServerDetector {

	/** Base query for domain */
	private static final String PTQL_QUERY_DOMAIN =
		"State.Name.eq=java," +
		"Args.*.ct=com.sun.aas.instanceRoot," +
		"Args.*.eq=-Dcom.sun.aas.instanceName=server";
		
	/** Base query for node agent */
	private static final String PTQL_QUERY_NODEAGENT =
		"State.Name.sw=java," +
		"Args.*.ct=com.sun.aas.instanceRoot," +
		"Args.*.ct=-Dcom.sun.aas.isNodeAgent=true";
	
	/** Base query for instance */
	private static final String PTQL_QUERY_INSTANCE = 
		"State.Name.eq=java," +
		"Args.*.ct=com.sun.aas.instanceRoot," +
		"Args.1.ct=nodeagents," + 
		"Args.*.ct=com.sun.aas.processLauncher=SE";
	
	public static final String VERSION_9 = "9.x";
	
	// names for servers as introduced in hq-plugin.xml
	public static final String NAME_INSTANCE = "Sun Glassfish Instance";
	public static final String NAME_DOMAIN = "Sun Glassfish Domain";
	public static final String NAME_NODEAGENT = "Sun Glassfish NodeAgent";
	

	/**
	 * Autoscan method.
	 */
	public List getServerResources(ConfigResponse platformConfig) 
		throws PluginException {
		getLog().info("Auto scanning");
		ArrayList servers = new ArrayList();
        List processes = getServerProcessList();
        
        for (int i = 0; i < processes.size(); i++) {
            GFProcess p = (GFProcess) processes.get(i);
            servers.addAll(getServerValues(p));
        }
		return servers;
	}

	/**
	 * Returns ptql query for processes we are currently trying
	 * to discover.
	 * 
	 * @return
	 */
	private String getQuery() {
		if (getTypeInfo().getName().equals(NAME_INSTANCE + " " + VERSION_9)) {
			return PTQL_QUERY_INSTANCE;
		} else if (getTypeInfo().getName().equals(NAME_DOMAIN + " " + VERSION_9)) {
			return PTQL_QUERY_DOMAIN;
		} else if (getTypeInfo().getName().equals(NAME_NODEAGENT + " " + VERSION_9)) {
			return PTQL_QUERY_NODEAGENT;			
		} else
			return null;
		
	}
	
	/**
	 * Matches currently processing server type.
	 * 
	 * @return
	 */
	private String getType() {
		if (getTypeInfo().getName().startsWith(NAME_INSTANCE)) {
			return NAME_INSTANCE;
		} else if (getTypeInfo().getName().startsWith(NAME_NODEAGENT)) {
			return NAME_NODEAGENT;
		} else if (getTypeInfo().getName().startsWith(NAME_DOMAIN)) {
			return NAME_DOMAIN;			
		} else 
			return null;
	}
	
	/**
	 * Returns list of individual servers found
	 * from process queries.
	 * 
	 * @return
	 */
	protected List getServerProcessList() {
		ArrayList servers = new ArrayList();
		long[] pids = null;

		pids = getPids(getQuery());

		
		if(pids != null) {
			for (int i = 0; i < pids.length; i++) {
				String path = getProcCwd(pids[i]);
				if (path == null) {
					continue;
				}
				GFProcess p = new GFProcess();
				p.path = path;
				p.pid = pids[i];
				p.query = getQuery();
				p.name = instanceNameFromArgs(pids[i]);
				p.version = VERSION_9;
				p.type = getType();
				getLog().info("instance name: " + instanceNameFromArgs(pids[i]));
				servers.add(p);
			}			
		}
		
		return servers;
	}
	
	/**
	 * Tries to find instance name from process arguments.
	 * 
	 * @param pid
	 * @return
	 */
	private String instanceNameFromArgs(long pid) {
		String name = null;
		String[] args = getProcArgs(pid);
		for (int i = 0; i < args.length; i++) {
			if(args[i].startsWith("-Dcom.sun.aas.instanceName=")) {
				String[] values = args[i].split("=");
				if(values.length == 2)
					name = values[1];
				break;
			}
		}
		return name;
	}
	
	/**
	 * Constructs list of server values.
	 */
	private List getServerValues(GFProcess p) 
		throws PluginException {
		List servers = new ArrayList();
		
		SunGlassfishDomainXml xml = new SunGlassfishDomainXml(p.path + "/domain.xml");
		String domain = xml.getDomain();
		
		String home = getParentDir(p.path);
		ServerResource server = createServerResource(home);

		server.setIdentifier(p.path);
		if(p.type.equals(NAME_DOMAIN)) {
			server.setName(server.getName() + " " + domain);
		} else {
			server.setName(server.getName() + " " + p.name);
		}
		server.setInstallPath(home);
		
		ConfigResponse config = new ConfigResponse();
		
		// update query to find individual servers.
		// domain:    -Dcom.sun.aas.domainName=domain1
		// instance:  -Dcom.sun.aas.instanceName=instance1
		// agent:     -Dcom.sun.aas.instanceName=agent1
		//
		if(p.type.equals(NAME_DOMAIN)) {
			p.query = p.query + ",Args.*.ct=com.sun.aas.domainName=" + domain;
		} else if(p.type.equals(NAME_INSTANCE)) {
			p.query = p.query + ",Args.*.ct=com.sun.aas.instanceName=" + p.name;			
		} else if(p.type.equals(NAME_NODEAGENT)) {
			p.query = p.query + ",Args.*.ct=com.sun.aas.instanceName=" + p.name;			
		}
		config.setValue("process.query", p.query);		
		config.setValue("instance", p.name);
		
		String jmxport = xml.getJmxPort(p.name,p.type);
		if(jmxport != null) {
			String url = "service:jmx:rmi:///jndi/rmi://localhost:" +
				jmxport + "/jmxrmi";
			config.setValue("jmx.url", url);
		}
		
		String dasport = xml.getDasPort();
		if(dasport != null)
			config.setValue("das.port", dasport);
		
		String dashost = xml.getDasHost();
		if(dashost != null)
			config.setValue("das.host", dashost);
		
		String dassecure = xml.getDasSecure();
		if(dassecure != null)
			config.setValue("das.secure", dassecure);
		
		if(domain != null)
			config.setValue("domain", domain);
		
		String passwordfile = findPasswordFile();
		if (passwordfile != null)
			config.setValue("das.passwordfile", passwordfile);
		
		File logfile = new File(home, "logs/server.log");
		if(logfile != null)
			config.setValue(LogFileTrackPlugin.PROP_FILES_SERVER, logfile.getAbsolutePath());
		server.setProductConfig(config);
		server.setControlConfig(getControlConfig(home, p));
		server.setMeasurementConfig();
		servers.add(server);
		
		return servers;
	}
	
	/**
	 * Constructs config used for controls.
	 * 
	 * @param installpath
	 * @return
	 */
    protected ConfigResponse getControlConfig(String installpath, GFProcess p) {
        ConfigResponse config = new ConfigResponse();
        
        File pidfile = new File(installpath, "config/.__com_sun_appserv_pid");
        
        config.setValue(ServerControlPlugin.PROP_PIDFILE, 
        				pidfile.getAbsolutePath());
        config.setValue(ServerControlPlugin.PROP_TIMEOUT, "300");

        File program = null;
		if(p.type.equals(NAME_DOMAIN)) {
	        program = isWin32() ? 
	        		new File(installpath, "../../bin/asadmin.bat") :
	       			new File(installpath, "../../bin/asadmin");
		} else {
	        program = isWin32() ? 
	        		new File(installpath, "../../../bin/asadmin.bat") :
	       			new File(installpath, "../../../bin/asadmin");
		}

		if(program != null)
			config.setValue(ServerControlPlugin.PROP_PROGRAM,
							program.getAbsolutePath());
        
        return config;
    }
    
    /**
     * When using asadmin command in glassfish, passwords can 
     * be stored to file to prevent exposing then to command line. 
     * Giving parameter to password file path lets user to escape 
     * from interactive shell.
     */
    protected String findPasswordFile() {
    	// AS_ADMIN_PASSWORD=value
    	// AS_ADMIN_ADMINPASSWORD=value
    	// AS_ADMIN_USERPASSWORD=value
    	// AS_ADMIN_MASTERPASSWORD=value
    	
    	// try to find few file alternatives locations.
    	// win: c:\asadminprefs
    	// unix /.asadminprefs
    	
    	File f;
    	if(isWin32()) {
    		f = new File("c:\\asadminprefs");    			
    	} else {
    		f = new File("/.asadminprefs");
    	}
    	if(f != null && f.isFile())
    		return f.getAbsolutePath();
    	else 
    		return null;
    }
	
    /**
     * Object storing information related to 
     * glassfish process.
     */
	protected class GFProcess {
		
		/** Work directory */
		String path;
		
		/** Process pid */
		long pid;
		
		/** Instance name from process arguments. */
		String name;
		
		/** PTQL query to find process. */
		String query;
		
		/** Version string for hq. */
		String version;
		
		/** Service type name. */
		String type;
	}
	
}
