package org.hyperic.hq.plugin.sunglassfish;

import java.io.File;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * Reads various configuration info from domain.xml.
 *
 */
public class SunGlassfishDomainXml {
	
	private Document doc;
	
	private static final Log log =
        LogFactory.getLog(SunGlassfishDomainXml.class.getName());
	
	private String dasPort;
	private String dasSecure;
	
	public SunGlassfishDomainXml(String file) {
		init(file);
		parseDasPort();
	}
	
	private void init(String file) {
		try {
			SAXBuilder builder = new SAXBuilder();
			doc = builder.build(new File(file));
		} catch (Exception e) {
			
		}
	}
	
	public String getDomain() {
		Element root = doc.getRootElement();
		Element e = find(root,"property","name","administrative.domain.name");
		if(e != null) {
			return e.getAttributeValue("value");
		}
		return null;
	}
	
	private void parseDasPort() {
		/**
		 * domain
		 *   configs
		 *     config
		 *       http-listener (default-virtual-server=__asadmin)
		 */
		Element root = doc.getRootElement();
		List configs = root.getChild("configs").getChildren("config");
		for(ListIterator i = configs.listIterator();i.hasNext();) {
			Element e = (Element)i.next();
			Element httpservice = e.getChild("http-service");
			Element listener = find(httpservice,"http-listener","default-virtual-server","__asadmin");
			if(listener != null) {
				dasPort = listener.getAttributeValue("port");
				dasSecure = listener.getAttributeValue("security-enabled");
				return;
			}
		}
	}
	
	public String getDasHost() {
		/**
		 * domain
		 *   configs
		 *     config
		 *       admin-service (type=das-and-server)
		 *         jmx-connector
		 *           property -> name="client-hostname" value=*
		 */
		Element root = doc.getRootElement();
		
		List configs = root.getChild("configs").getChildren("config");
		for(ListIterator i = configs.listIterator();i.hasNext();) {
			Element e = find((Element)i.next(),"admin-service","type","das-and-server");
			if(e != null) {
				Element p = e.getChild("jmx-connector");
				Element das = find(p,"property","name","client-hostname");
				if(das != null) {
					String h = das.getAttributeValue("value");
					if(h != null)
						return h;
				}
			}
		}		
		return null;
	}
	
	private Element find(Element e, String findName, String findAttr, String match) {
		List elements = e.getChildren(findName);
		ListIterator i = elements.listIterator();
		while(i.hasNext()) {
			Element node = (Element)i.next();
			String a = node.getAttributeValue(findAttr);
			if(a != null) {
				if(a.equals(match))
					return node;
			}
		}
		return null;
	}
	
	public String getJmxPort(String id, String type) {
		/**
		 * jmx ports are defined two places.
		 * For servers and instances, navigate to
		 * domain
		 *   configs
		 *     config
		 *       admin-service
		 *         jmx-connector -> port
		 *         
		 * port may be static value(8686) or reference(${JMX_SYSTEM_CONNECTOR_PORT})
		 * 
		 * In case of reference, navigate in same config section to 
		 * domain
		 *   configs
		 *     config
		 *       system-property -> name=JMX_SYSTEM_CONNECTOR_PORT
		 *       
		 * jmx port for agents are in:
		 * domain
		 *   node-agents
		 *     node-agent
		 *       jmx-connector -> port
		 */
		
		log.info("Finding jmx port for " + id + " with type " + type);
		Element root = doc.getRootElement();
		
		if(type.equals(SunGlassfishServerDetector.NAME_NODEAGENT)) {
						
			Element nodeAgents = root.getChild("node-agents");
			List agents = nodeAgents.getChildren();
			ListIterator i = agents.listIterator();
			while(i.hasNext()) {
				Element e = (Element)i.next();
				if(e.getAttributeValue("name").equals(id)) {
					Element jmxConnector = e.getChild("jmx-connector");
					return jmxConnector.getAttributeValue("port");
				}
			}
		} else {
			Element serversE = root.getChild("servers");
			Element serverE = null;
			List servers = serversE.getChildren("server");
			for(ListIterator i = servers.listIterator();i.hasNext();) {
				Element e = (Element)i.next();
				if(e.getAttributeValue("name").equals(id)) {
					serverE = e;
					break;
				}
			}
			
			String configref = serverE.getAttributeValue("config-ref");
			
			Element configsE = root.getChild("configs");
			Element configE = null;	
			List configs = configsE.getChildren("config");
			for(ListIterator i = configs.listIterator();i.hasNext();) {
				Element e = (Element)i.next();
				if(e.getAttributeValue("name").equals(configref)) {
					configE = e;
					break;
				}
			}
			
			Element adminserviceE = configE.getChild("admin-service");
			Element jmxconnectorE = adminserviceE.getChild("jmx-connector");
			String port = jmxconnectorE.getAttributeValue("port");
			boolean valid = false;
			try {
				Integer.parseInt(port);
				valid = true;
			} catch (NumberFormatException e) {
				valid = false;
			}
			
			if(valid)
				return port;
			else {
				List propertys = configE.getChildren("system-property");
				for(ListIterator i = propertys.listIterator();i.hasNext();) {
					Element systempropertyE = (Element)i.next();
					if(port.substring(2, port.length()-1).equals(systempropertyE.getAttributeValue("name"))) {
						return systempropertyE.getAttributeValue("value");
					}
				}
			}
			
		}
		
		return null;
	}

	public String getDasPort() {
		return dasPort;
	}

	public void setDasPort(String dasPort) {
		this.dasPort = dasPort;
	}

	public String getDasSecure() {
		return dasSecure;
	}

	public void setDasSecure(String dasSecure) {
		this.dasSecure = dasSecure;
	}
	
	
}