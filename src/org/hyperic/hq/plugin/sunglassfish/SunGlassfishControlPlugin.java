package org.hyperic.hq.plugin.sunglassfish;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.Metric;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.PluginManager;
import org.hyperic.hq.product.ServerControlPlugin;
import org.hyperic.util.ArrayUtil;
import org.hyperic.util.StringUtil;
import org.hyperic.util.config.ConfigResponse;

public class SunGlassfishControlPlugin extends ServerControlPlugin {

	private static final Log log =
        LogFactory.getLog(SunGlassfishControlPlugin.class.getName());
	
    private List actions;

    private static final List ACTIONS =
        Arrays.asList(new String[] { "start", "stop", "restart" });

    public void init(PluginManager manager)
        throws PluginException {

        super.init(manager);

        this.actions = super.getActions();
        if (this.actions.size() == 0) {
            this.actions = ACTIONS;
        }
    }

    public void configure(ConfigResponse config)
        throws PluginException {

        super.configure(config);

    }

    public List getActions() {
        return this.actions;
    }

    protected boolean isBackgroundCommand() {
        return false;
    }

    protected String[] getArgs(String action) {
        String[] args = new String[] {};
        String cmdline = getTypeProperty(action + ".args");

        if (cmdline != null) {
            cmdline = Metric.translate(cmdline, getConfig());
            String[] opts = StringUtil.explodeQuoted(cmdline);
            for(int i = 0; i < opts.length; i++) {
            	opts[i] = Metric.decode(opts[i]);
            }
            return (String[])ArrayUtil.combine(args, opts);
        }
        else {
            return args;
        }
    }


    public void doAction(String action, String[] args)
        throws PluginException {

        if (action.equals("start")) {
            start();
        }
        else if (action.equals("restart")) {
            restart();
        }
        else {
            stop();
        }
    }

    public int start() {
        int status = doCommand(getArgs("start"));
        log.info("start() status=" + status + ". " + getMessage());
        if (status == RESULT_SUCCESS) {
            setMessage("start executed successfully");
        }
        return status;
    }

    public int stop() {
        int status = doCommand(getArgs("stop"));
        log.info("stop() status=" + status + ". " + getMessage());
        if (status == RESULT_SUCCESS) {
            setMessage("stop executed successfully");
        }
        return status;
    }

    public int restart() {
        int stopStatus = stop();
        try {
            // don't like doing this, but we need to wait sometime for
            // process cleanup to occur like network ports freeing up
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        int startStatus = start();
        if (startStatus == RESULT_SUCCESS && stopStatus == RESULT_SUCCESS) {
            setMessage("restart executed successfully");
            return RESULT_SUCCESS;
        } else if (startStatus != RESULT_SUCCESS &&
                   stopStatus != RESULT_SUCCESS) {
            setMessage("restart failed");
        } else if (startStatus == RESULT_SUCCESS &&
                   stopStatus != RESULT_SUCCESS) {
            setMessage("stop failed but start succeeded");
        } else if (startStatus != RESULT_SUCCESS &&
                   stopStatus == RESULT_SUCCESS) {
            setMessage("stop succeeded but start failed");
        }
        return RESULT_FAILURE;
    }

	
}
