/**
 * $Id$
 */
package com.untangle.app.ip_reputation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.net.InetAddress;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.untangle.app.webroot.WebrootQuery;
import com.untangle.app.webroot.WebrootDaemon;

import com.untangle.uvm.vnet.Protocol;
import com.untangle.uvm.app.PortRange;
import com.untangle.uvm.app.IPMaskedAddress;
import com.untangle.uvm.vnet.Subscription;


import com.untangle.uvm.HookCallback;
import com.untangle.uvm.HookManager;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.SettingsManager;
import com.untangle.uvm.SessionMatcher;
import com.untangle.uvm.app.AppSettings;
import com.untangle.uvm.app.AppProperties;
import com.untangle.uvm.app.AppMetric;
import com.untangle.uvm.app.IPMaskedAddress;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.app.AppBase;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.AppTCPSession;
import com.untangle.uvm.vnet.AppSession;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.IPNewSessionRequest;
import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.vnet.SessionAttachments;
import com.untangle.uvm.vnet.Token;
import com.untangle.app.http.HeaderToken;
import com.untangle.uvm.app.IntMatcher;

/** FirewalApp is the IP Reputation Application implementation */
public class IpReputationApp extends AppBase
{
    private final Logger logger = Logger.getLogger(getClass());

    public List<IPMaskedAddress> localNetworks = null;
    public WebrootQuery webrootQuery = null;

    private static AtomicInteger AppCount = new AtomicInteger();
    private final IpReputationDecisionEngine engine = new IpReputationDecisionEngine(this);

    private static final String STAT_BLOCK = "block";
    private static final String STAT_FLAG = "flag";
    private static final String STAT_PASS = "pass";
    private static final String STAT_LOOKUP_AVG = "lookup_avg";

    protected final IpReputationReplacementGenerator replacementGenerator;
    
    private final Subscription httpsSub = new Subscription(Protocol.TCP, IPMaskedAddress.anyAddr, PortRange.ANY, IPMaskedAddress.anyAddr, new PortRange(443, 443));

    // For converse, it it a bad idea to have multiple subscripitions?
    // UDP, all
    // TCP,1-79, 
    // TCP 81-442
    // TCP 443+

    private final PipelineConnector httpConnector = UvmContextFactory.context().pipelineFoundry().create("web-filter-http", this, null, new IpReputationHttpHandler(this), Fitting.HTTP_TOKENS, Fitting.HTTP_TOKENS, Affinity.CLIENT, -2000, true);
    private final PipelineConnector httpsSniConnector = UvmContextFactory.context().pipelineFoundry().create("web-filter-https-sni", this, httpsSub, new IpReputationHttpsSniHandler(this), Fitting.OCTET_STREAM, Fitting.OCTET_STREAM, Affinity.CLIENT, -2000, true);
    private final PipelineConnector otherConnector = UvmContextFactory.context().pipelineFoundry().create("ip_reputation", this, null, new IpReputationEventHandler(this), Fitting.OCTET_STREAM, Fitting.OCTET_STREAM, Affinity.CLIENT, -2000, false);
    private final PipelineConnector[] connectors = new PipelineConnector[] { httpConnector, httpsSniConnector, otherConnector };

    private static final HookCallback WebrootQueryGetUrlInfoHook;


    public static final Map<Integer, Integer> UrlCatThreatMap;
    public static final Map<Integer, String> ReputationThreatMap;
    static {
        WebrootQueryGetUrlInfoHook = new IpReputationWebrootQueryGetUrlInfoHook();
        UvmContextFactory.context().hookManager().registerCallback( HookManager.WEBFILTER_BASE_CATEGORIZE_SITE, WebrootQueryGetUrlInfoHook );

        UrlCatThreatMap = new HashMap<>();
        UrlCatThreatMap.put(71, 1);         // Spam
        UrlCatThreatMap.put(67, 16);        // Botnets
        UrlCatThreatMap.put(57, 256);       // Phishing
        UrlCatThreatMap.put(58, 512);       // Proxy
        UrlCatThreatMap.put(49, 655362);    // Keyloggers
        UrlCatThreatMap.put(56, 131072);    // Malware
        UrlCatThreatMap.put(59, 262144);    // Spyware

        ReputationThreatMap = new HashMap<>();
        ReputationThreatMap.put(0, "No reputation");
        ReputationThreatMap.put(20, "High Risk");
        ReputationThreatMap.put(40, "Suspicious");
        ReputationThreatMap.put(60, "Moderate Risk");
        ReputationThreatMap.put(80, "Low Risk");
        ReputationThreatMap.put(100, "Trustworthy");
    }

    private IpReputationSettings settings = null;

    /**
     * This is used to reset sessions that are blocked by ip reputation when they switch policy
     */
    private final SessionMatcher IP_REPUTATION_SESSION_MATCHER = new SessionMatcher() {
            /**
             * isMatch returns true if the session matches a block rule
             * @param policyId
             * @param protocol
             * @param clientIntf
             * @param serverIntf
             * @param clientAddr
             * @param serverAddr
             * @param clientPort
             * @param serverPort
             * @param attachments
             * @return true if the session should be reset
             */
            public boolean isMatch( Integer policyId, short protocol,
                                    int clientIntf, int serverIntf,
                                    InetAddress clientAddr, InetAddress serverAddr,
                                    int clientPort, int serverPort,
                                    SessionAttachments attachments )
            {
                // logger.warn(handler);
                // if (handler == null)
                //     return false;

                IpReputationPassRule matchedRule = null;
                
                /**
                 * Find the matching rule compute block/log verdicts
                 */
                for (IpReputationPassRule rule : settings.getPassRules()) {
                    if (rule.isMatch(protocol,
                                     clientIntf, serverIntf,
                                     clientAddr, serverAddr,
                                     clientPort, serverPort,
                                     attachments)) {
                        matchedRule = rule;
                        break;
                    }
                }
        
                if (matchedRule == null)
                    return false;

                logger.info("IP Reputation Save Setting Matcher: " +
                            clientAddr.getHostAddress().toString() + ":" + clientPort + " -> " +
                            serverAddr.getHostAddress().toString() + ":" + serverPort +
                            " :: pass:" + matchedRule.getPass());
                
                return matchedRule.getPass() == false;
                // return false;
            }
    };
    
    /**
     * IP Reputation App constructor
     * @param appSettings - the AppSettings
     * @param appProperties the AppProperties
     */
    public IpReputationApp( AppSettings appSettings, AppProperties appProperties )
    {
        super( appSettings, appProperties );

        this.replacementGenerator = buildReplacementGenerator();

        // Calculate home networks as a uvm network function
        //  // Just pull context?  Would have to contentw with chnges, right?
        // this.homeNetworks = this.calculateHomeNetworks( UvmContextFactory.context().networkManager().getNetworkSettings());
        
        // getlocalNetworks
        // this.networkSettingsChangeHook = new IntrusionPreventionNetworkSettingsHook();
        //      this should just get local network list for us.
        localNetworks = UvmContextFactory.context().networkManager().getLocalNetworks();

        // this.handler = new IpReputationEventHandler(this);

        this.addMetric(new AppMetric(STAT_PASS, I18nUtil.marktr("Sessions passed")));
        this.addMetric(new AppMetric(STAT_FLAG, I18nUtil.marktr("Sessions flagged")));
        this.addMetric(new AppMetric(STAT_BLOCK, I18nUtil.marktr("Sessions blocked")));
        this.addMetric(new AppMetric(STAT_LOOKUP_AVG, I18nUtil.marktr("Lookup time average"), 0L, AppMetric.Type.AVG_TIME, I18nUtil.marktr("ms"), true));
        // this.addMirroredMetrics( WebrootDaemon );
        // this.addMirroredMetrics( WebrootQuery );



        // !!! underscore, single word, or dash?
        // this.connector = UvmContextFactory.context().pipelineFoundry().create("ip_reputation", this, null, handler, Fitting.OCTET_STREAM, Fitting.OCTET_STREAM, Affinity.CLIENT, -2000, false);
        // this.connectors = new PipelineConnector[] { connector };
    }

    /**
     * Called to get our decision engine instance
     * 
     * @return The decision engine
     */
    public IpReputationDecisionEngine getDecisionEngine()
    {
        return engine;
    }

    /**
     * Get the current IP Reputation Settings
     * @return IpReputationSettings
     */
    public IpReputationSettings getSettings()
    {
        return settings;
    }

    /**
     * Set the current IP Reputation settings
     * @param newSettings
     */
    public void setSettings(final IpReputationSettings newSettings)
    {
        /**
         * set the new ID of each rule
         * We use 100,000 * appId as a starting point so rule IDs don't overlap with other ip reputation
         *
         * Also set flag to true if rule is blocked
         */
        int idx = this.getAppSettings().getPolicyId().intValue() * 100000;
        for (IpReputationPassRule rule : newSettings.getPassRules()) {
            rule.setRuleId(++idx);

            // if (rule.getBlock())
            //     rule.setFlag(true);
        }

        /**
         * Save the settings
         */
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String appID = this.getAppSettings().getId().toString();
        try {
            settingsManager.save( System.getProperty("uvm.settings.dir") + "/" + "ip-reputation/" + "settings_"  + appID + ".js", newSettings );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to save settings.",e);
            return;
        }

        /**
         * Change current settings
         */
        this.settings = newSettings;
        try {logger.debug("New Settings: \n" + new org.json.JSONObject(this.settings).toString(2));} catch (Exception e) {}

        this.reconfigure();
    }

    /**
     * Get the current ruleset
     * @return the list
     */
    public List<IpReputationPassRule> getPassRules()
    {
        if (getSettings() == null)
            return null;
        
        return getSettings().getPassRules();
    }

    /**
     * Set the current ruleset
     * @param rules - the new rules
     */
    public void setRules( List<IpReputationPassRule> rules )
    {
        IpReputationSettings set = getSettings();

        if (set == null) {
            logger.warn("NULL settings");
            return;
        }

        set.setPassRules(rules);
        setSettings(set);
    }
    
    
    /**
     * Increment the block stat
     */
    public void incrementBlockCount() 
    {
        this.incrementMetric(STAT_BLOCK);
    }

    /**
     * Increment the pass stat
     */
    public void incrementPassCount() 
    {
        this.incrementMetric(STAT_PASS);
    }

    /**
     * Increment the flag stat
     */
    public void incrementFlagCount() 
    {
        this.incrementMetric(STAT_FLAG);
    }

    /**
     * Add new time to total time counter.
     * @param time Long of time to add.
     */
    public void adjustLookupAverage(long time)
    {
        this.adjustMetric(STAT_LOOKUP_AVG, time);
    }

    /**
     * Return various local valus for use with reports.
     *
     * @param  key String of key in settings.
     * @param arguments Array of String arguments to pass.
     * @return     List of JSON objects for the settings.
     */
    public JSONArray getReportInfo(String key, String... arguments){
        JSONArray result = null;
        int index = 0;

        if(key.equals("localNetworks")){
            result = new JSONArray();
            try{
                for(IPMaskedAddress address : localNetworks){
                    JSONObject jo = new JSONObject(address);
                    jo.remove("class");
                    result.put(index++, jo);
                }
            }catch(Exception e){
                logger.warn("getReportnfo:", e);
            }
        }else if(key.equals("getUrlHistory")){
            return webrootQuery.getUrlHistory(arguments);
        }else if(key.equals("getIpHistory")){
            return webrootQuery.getIpHistory(arguments);
        }

        return result;
    }

    /**
     * Generate a response
     * 
     * @param nonce
     *        The nonce
     * @param session
     *        The session
     * @param uri
     *        The URI
     * @param header
     *        The header
     * @return The response token
     */
    public Token[] generateHttpResponse(String nonce, AppTCPSession session, String uri, HeaderToken header)
    {
        return replacementGenerator.generateResponse(nonce, session, uri, header);
    }

    /**
     * [getThreatFromReputation description]
     * @param  reputation [description]
     * @return            [description]
     */
    String getThreatFromReputation(Integer reputation)
    {
        return ReputationThreatMap.get(reputation > 0 ? ( reputation - (reputation % 20) + 20 ) : 0);
    }

    /**
     * Build a replacement generator
     * 
     * @return The replacement generator
     */
    protected IpReputationReplacementGenerator buildReplacementGenerator()
    {
        return new IpReputationReplacementGenerator(getAppSettings());
    }

    /**
     * Get the block details for the argumented nonce
     * 
     * @param nonce
     *        The nonce to search
     * @return Block details
     */
    public IpReputationBlockDetails getDetails(String nonce)
    {
        return replacementGenerator.getNonceData(nonce);
    }

    /**
     * Generate a nonce
     * 
     * @param details
     *        The block details
     * @return The nonce
     */
    protected String generateNonce(IpReputationBlockDetails details)
    {
        return replacementGenerator.generateNonce(details);
    }

    /**
     * Get the Pipeline connectors
     * @return the pipeline connectors array
     */
    @Override
    protected PipelineConnector[] getConnectors()
    {
        return this.connectors;
    }

    /**
     * preStart
     * @param isPermanentTransition
     */
    @Override
    protected void preStart( boolean isPermanentTransition )
    {
        this.reconfigure();
        WebrootDaemon.getInstance().start();
        webrootQuery = WebrootQuery.getInstance();
    }

    /**
     * postStart()
     * @param isPermanentTransition
     */
    @Override
    protected void postStart( boolean isPermanentTransition )
    {
        startServlet(logger);
        killAllSessions();
    }

    /**
     * preStop()
     * @param isPermanentTransition
     */
    @Override
    protected void preStop( boolean isPermanentTransition )
    {
        webrootQuery = null;
        WebrootDaemon.getInstance().stop();
        stopServlet(logger);
    }

    /**
     * postStop()
     * @param isPermanentTransition
     */
    @Override
    protected void postStop( boolean isPermanentTransition )
    {
        killAllSessions();
    }

    /**
     * postInit()
     */
    @Override
    protected void postInit()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String appID = this.getAppSettings().getId().toString();
        IpReputationSettings readSettings = null;
        String settingsFileName = System.getProperty("uvm.settings.dir") + "/ip-reputation/" + "settings_" + appID + ".js";

        try {
            readSettings = settingsManager.load( IpReputationSettings.class, settingsFileName );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to load settings:",e);
        }
        
        /**
         * If there are still no settings, just initialize
         */
        if (readSettings == null) {
            logger.warn("No settings found - Initializing new settings.");
            setSettings(getDefaultSettings());
        }
        else {
            logger.info("Loading Settings...");

            // UPDATE settings if necessary
            
            this.settings = readSettings;
            logger.debug("Settings: " + this.settings.toJSONString());
        }

        this.reconfigure();
    }

    /**
     * Create new default settings
     * @return the default settings
     */
    private IpReputationSettings getDefaultSettings()
    {
        logger.info("Creating the default settings...");

        /* A few sample settings */
        List<IpReputationPassRule> ruleList = new LinkedList<>();
        LinkedList<IpReputationPassRuleCondition> matcherList = null;
            
        // /* example rule 1 */
        // IpReputationRuleCondition portMatch1 = new IpReputationRuleCondition(IpReputationRuleCondition.ConditionType.DST_PORT, "21");
        // matcherList = new LinkedList<>();
        // matcherList.add(portMatch1);
        // ruleList.add(new IpReputationRule(false, matcherList, true, true, "Block and flag all traffic destined to port 21"));
                             
        // /* example rule 2 */
        // IpReputationRuleCondition addrMatch2 = new IpReputationRuleCondition(IpReputationRuleCondition.ConditionType.SRC_ADDR, "1.2.3.4/255.255.255.0");
        // matcherList = new LinkedList<>();
        // matcherList.add(addrMatch2);
        // ruleList.add(new IpReputationRule(false, matcherList, true, true, "Block and flag all TCP traffic from 1.2.3.0 netmask 255.255.255.0"));

        // /* example rule 3 */
        // IpReputationRuleCondition addrMatch3 = new IpReputationRuleCondition(IpReputationRuleCondition.ConditionType.DST_ADDR, "1.2.3.4/255.255.255.0");
        // IpReputationRuleCondition portMatch3 = new IpReputationRuleCondition(IpReputationRuleCondition.ConditionType.DST_PORT, "1000-5000");
        // matcherList = new LinkedList<>();
        // matcherList.add(addrMatch3);
        // matcherList.add(portMatch3);
        // ruleList.add(new IpReputationRule(false, matcherList, true, false, "Accept and flag all traffic to the range 1.2.3.1 - 1.2.3.10 to ports 1000-5000"));

        IpReputationSettings settings = new IpReputationSettings(ruleList);
        settings.setVersion(1);
        
        return settings;
    }

    /**
     * Call reconfigure() after setting settings to
     * affect all new settings
     */
    private void reconfigure() 
    {
        logger.info("Reconfigure()");

        /* check for any sessions that should be killed according to new rules */
        this.killMatchingSessions(IP_REPUTATION_SESSION_MATCHER);

        if (settings == null) {
            logger.warn("Invalid settings: null");
        } else {
            // handler.configure(settings);
        }
    }

    /**
     * Hook into network setting saves.
     */
    static private class IpReputationWebrootQueryGetUrlInfoHook implements HookCallback
    {
        private static final Logger hookLogger = Logger.getLogger(IpReputationWebrootQueryGetUrlInfoHook.class);
        /**
        * @return Name of callback hook
        */
        public String getName()
        {
            return "ip-reputation-categorize-site";
        }

        /**
         * Callback documentation
         *
         * @param args  Args to pass
         */
        public void callback( Object... args )
        {
            AppTCPSession sess = (AppTCPSession) args[0];
            Integer reputation = (Integer) args[1];
            @SuppressWarnings("unchecked")
            List<Integer> categories = (List<Integer>) args[2];

            if(sess == null || reputation == null || categories == null){
                return;
            }
            if ( ! (sess instanceof AppTCPSession) ) {
                hookLogger.warn( "Invalid session: " + sess);
                return;
            }
            int threatmask = 0;
            for(Integer category : categories){
                if(UrlCatThreatMap.get(category) != null){
                    threatmask += UrlCatThreatMap.get(category);
                }
            }

            sess.globalAttach(AppSession.KEY_IP_REPUTATION_SERVER_REPUTATION, reputation);
            sess.globalAttach(AppSession.KEY_IP_REPUTATION_SERVER_THREATMASK, threatmask);

        }
    }

    /**
     * Deploy the web app
     *
     * @param logger
     *        The logger
     */
    private static synchronized void startServlet(Logger logger)
    {
        boolean firstIn = AppCount.get() == 0;
        AppCount.incrementAndGet();
        if(firstIn == false){
            return;
        }

        if (UvmContextFactory.context().tomcatManager().loadServlet("/ip-reputation", "ip-reputation") != null) {
            logger.debug("Deployed IpReputation WebApp");
        } else {
            logger.error("Unable to deploy IpReputation WebApp");
        }
    }

    /**
     * Undeploy the web app
     * 
     * @param logger
     *        The logger
     */
    private static synchronized void stopServlet(Logger logger)
    {
        boolean lastOut = AppCount.decrementAndGet() == 0;
        if(lastOut  == false){
            return;
        }

        if (UvmContextFactory.context().tomcatManager().unloadServlet("/ip-reputation")) {
            logger.debug("Unloaded IpReputation WebApp");
        } else {
            logger.warn("Unable to unload IpReputation WebApp");
        }
    }

}