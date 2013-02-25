/**
 * $Id$
 */
package com.untangle.uvm.engine;

import java.net.ConnectException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.io.File;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.untangle.jnetcap.Netcap;
import com.untangle.uvm.IntfConstants;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.UvmState;
import com.untangle.uvm.NetworkManager;
import com.untangle.uvm.networking.InterfaceConfiguration;
import com.untangle.uvm.networking.NetworkConfiguration;
import com.untangle.uvm.networking.NetworkConfigurationListener;
import com.untangle.uvm.networking.IPNetwork;
import com.untangle.uvm.node.SessionTuple;
import com.untangle.uvm.node.IPAddress;
import com.untangle.uvm.node.IPMatcher;
import com.untangle.uvm.node.OpenVpn;
import com.untangle.uvm.util.JsonClient;
import com.untangle.uvm.util.XMLRPCUtil;
import com.untangle.uvm.SettingsManager;

/**
 * The Network Manager handles all the network configuration
 */
public class NetworkManagerImpl implements NetworkManager
{
    private static final String ETC_INTERFACES_FILE = "/etc/network/interfaces";
    private static final String ETC_RESOLV_FILE = "/etc/resolv.conf";

    private final Logger logger = Logger.getLogger(getClass());

    private static final Object lock = new Object();

    /* networkListeners stores parties interested in being notified of network changes */
    private Set<NetworkConfigurationListener> networkListeners = new HashSet<NetworkConfigurationListener>();

    /** The nuts and bolts of networking, the real bits of panther.  this my friend
     * should never be null */
    private NetworkConfiguration networkConfiguration = null;

    public NetworkManagerImpl()
    {
        try {
            this.networkConfiguration = loadNetworkConfiguration( );
        
            /* Update the link status for all of the interfaces */
            updateLinkStatus();
        } catch ( Exception e ) {
            logger.error( "Exception initializing settings, using blank defaults", e );
            this.networkConfiguration = new NetworkConfiguration();
            this.networkConfiguration.setHostname("hostname.example.com");
            this.networkConfiguration.setInterfaceList(new LinkedList<InterfaceConfiguration>());
            this.networkConfiguration.setDnsServerEnabled(true);
            this.networkConfiguration.setDnsLocalDomain("example.com");
            this.networkConfiguration.setDhcpServerEnabled(true);
        }

    }

    public void updateLinkStatus()
    {
        InterfaceTester.getInstance().updateLinkStatus( this.networkConfiguration );
    }

    /* Return the primary address of the device, this is the primary
     * external address.  which is the first address registered on the
     * first network space */
    public IPAddress getPrimaryAddress()
    {
        NetworkConfiguration settings = this.networkConfiguration;

        if ( settings == null )
            return null;

        InterfaceConfiguration wan = settings.findFirstWAN();

        if ( wan == null )
            return null;

        IPNetwork primary = wan.getPrimaryAddress();

        if ( primary == null) {
            logger.warn("NULL primary address for wan: " + wan.getName());
            return null;
        }
        
        return primary.getNetwork();
    }

    public NetworkConfiguration getNetworkConfiguration()
    {
        return this.networkConfiguration;
    }

    public void remapInterfaces( String[] osArray, String[] userArray ) throws Exception
    {
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put( "os_names", new JSONArray( osArray ));
            jsonObject.put( "user_names", new JSONArray( userArray ));
        } catch ( JSONException e ) {
            throw new Exception( "Unable to build JSON Object", e );
        }

//         try {
//             JsonClient.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "remap_interfaces", jsonObject );
//         } catch ( Exception e ) {
//             throw new Exception( "Unable to configure the external interface.", e );
//         }
    }

    /* Get the current hostname */
    public String getHostname()
    {
        String hostname = this.networkConfiguration.getHostname();
        if (hostname == null)
            return "";
        return hostname;
    }

    public InterfaceConfiguration setSetupSettings( InterfaceConfiguration wan ) throws Exception
    {
        return null;
    }

    /**
     * Returns the first WAN
     */
    public InterfaceConfiguration getWizardWAN()
    {
        return getNetworkConfiguration().findFirstWAN();
    }

    /**
     * returns a recommendation for the internal network. 
     * @param externalAddress The external address, if null, this uses the external address of the box.
     */
    public IPNetwork getWizardInternalAddressSuggestion( IPAddress externalAddress )
    {
        try {
            NetworkConfiguration netConf = getNetworkConfiguration();

            if ( netConf == null ) {
                logger.warn("NULL network configuration");
                return IPNetwork.parse( "192.168.1.1/24" );
            }
            
            if ( externalAddress == null ) { 
                externalAddress = netConf.findFirstWAN().getPrimaryAddress().getNetwork();
            }
            
            if ( externalAddress == null ) {
                return IPNetwork.parse( "192.168.1.1/24" );
            }

            /**
             * Use the already existing internal addr by default
             * However, If it overlaps with external - suggest something else
             */
            InterfaceConfiguration internal = netConf.findByName("Internal");
            if (internal != null) {
                IPNetwork internalNet = internal.getPrimaryAddress();

                /**
                 * If it doesn't have a current address just use 192.168.1.1
                 */
                if (internalNet == null)
                    internalNet = IPNetwork.parse( "192.168.1.1/24" );
                    
                IPMatcher internalMatcher = new IPMatcher( internalNet );

                if ( internalMatcher.isMatch( externalAddress.getAddr() ) ) {
                    return IPNetwork.parse( "172.16.0.1/24" );
                }

                return internalNet;
            }
            
            return IPNetwork.parse( "192.168.1.1/24" );
            
        } catch ( Exception e ) {
            /* This should never happen */
            throw new RuntimeException( "Unable to suggest an internal address", e );
        }
    }

    public void setWizardNatEnabled( IPAddress address, IPAddress netmask, boolean enableDhcpServer )
        throws Exception
    {
        // FIXME
    }

    public void setWizardNatDisabled() throws Exception
    {
        // FIXME
    }

    public Boolean isQosEnabled()
    {
        return null;
//         try {
//             JSONObject jsonObject = JsonClient.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "get_qos_settings", null );

//             JSONObject result = jsonObject.getJSONObject("result");
//             if (result == null)
//                 return Boolean.FALSE;

//             try {
//                 Boolean enabled = result.getBoolean("enabled");
//                 if (enabled == null) {
//                     logger.warn("Unable to retrieve QoS settings: null");
//                 }
//                 return enabled;
//             }
//             catch (org.json.JSONException e) {
//                 //org.json.JSONException: JSONObject["enabled"] is not a Boolean.
//                 //assume its off
//                 return false;
//             }
            
//         } catch (Exception e) {
//             logger.error("Unable to retrieve QoS settings:",e);
//             return null;
//         }
    }
    
    public JSONArray getWANSettings()
    {
        return null;
//         try {
//             JSONObject jsonObject = JsonClient.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "get_wan_interfaces", null );
//             JSONArray result = jsonObject.getJSONArray("result");
//             return result;
            
//         } catch (Exception e) {
//             logger.error("Unable to retrieve WAN settings:",e);
//             return null;
//         }
    }

    public void enableQos()
    {
        return;
//         try {
//             JSONObject jsonObject = JsonClient.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "enable_qos", null );
//             return;
//         } catch (Exception e) {
//             logger.error("Unable to enable Qos:",e);
//             return;
//         }
    }

    private void _setWANSpeed(String name, String property, int speed)
    {
        return;
        //         try {
//             JSONObject jsonObject = new JSONObject();
//             jsonObject.put("name",name);
//             jsonObject.put(property,speed);

//             jsonObject = JsonClient.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "set_wan_speed", jsonObject );
//             return;
//         } catch (Exception e) {
//             logger.error("Unable to set WAN settings:",e);
//             return;
//         }
    }
    
    public void setWANDownloadBandwidth(String name, int speed)
    {
        this._setWANSpeed(name,"download_bandwidth",speed);
    }

    public void setWANUploadBandwidth(String name, int speed)
    {
        this._setWANSpeed(name,"upload_bandwidth",speed);
    }
    
    public void refreshNetworkConfig()
    {
        /**
         * If the UVM is shutting down, don't bother doing anything
         */
        if ( UvmContextFactory.context().state() != UvmState.RUNNING)
            return;

        logger.info("Refreshing Network Configuration...");

        this.networkConfiguration = loadNetworkConfiguration( );

        if (this.networkConfiguration == null) {
            logger.error("Failed to load network configuration, creating reasonable default.");
            this.networkConfiguration = new NetworkConfiguration();
        }
        
        if ( logger.isDebugEnabled()) {
            logger.debug( "New network settings: " + this.networkConfiguration );
        }

        try {
            callNetworkListeners();
        } catch ( Exception e ) {
            logger.error( "Exception in a listener", e );
        }

        logger.info("Refreshed  Network Configuration.");
    }

    public boolean isWanInterface( int intfId )
    {
        InterfaceConfiguration intfConf = getNetworkConfiguration().findById( intfId );
        if (intfConf == null) {
            logger.warn("Unabled to find interface: " + intfId);
            return false;
        }

        return intfConf.isWAN();
    }
    
    public String[] getPossibleInterfaces()
    {
        LinkedList<String> possibleInterfaces = new LinkedList<String>();

        /**
         * These are always possible
         */
        possibleInterfaces.add("any");
        possibleInterfaces.add("wan");
        possibleInterfaces.add("non_wan");

        if (this.networkConfiguration != null) {   
            for ( InterfaceConfiguration intfConf : this.networkConfiguration.getInterfaceList() ) {
                possibleInterfaces.add(intfConf.getInterfaceId().toString());
            }
        }

        return possibleInterfaces.toArray(new String[0]);
    }

    public String[] getWanInterfaces()
    {
        LinkedList<String> wanInterfaces = new LinkedList<String>();

        if (this.networkConfiguration != null) {   
            for ( InterfaceConfiguration intfConf : this.networkConfiguration.getInterfaceList() ) {
                if (intfConf.isWAN())
                    wanInterfaces.add(intfConf.getInterfaceId().toString());
            }
        }

        return wanInterfaces.toArray(new String[0]);
    }
    
    /*
     * This returns an address where the host should be able to access
     * HTTP.  if HTTP is not reachable, this returns NULL
     */
    public InetAddress getInternalHttpAddress( int clientIntf )
    {
        /* Retrieve the network settings */
        NetworkConfiguration netConf = this.networkConfiguration;
        if ( netConf == null ) {
            logger.warn("Failed to fetch network configuration");
            return null;
        }

        InterfaceConfiguration intfConf = netConf.findById( clientIntf );
        if ( intfConf == null ) {
            logger.warn("Failed to fetch interface configuration");
            return null;
        }

        /* WAN ports never have HTTP open */
        boolean isWan = intfConf.isWAN();
        if ( isWan ) {
            //this is normal no error logged
            return null;
        }
        
        /**
         * If this interface is bridged with another, use the addr from the other
         */
        if (InterfaceConfiguration.CONFIG_BRIDGE.equals(intfConf.getConfigType())) {
            String bridgedTo = intfConf.getBridgedTo();
            intfConf = netConf.findByName( bridgedTo );

            if ( intfConf == null ) {
                logger.warn("No Interface found for name: " + bridgedTo );
                return null;
            }
        }

        /**
         * The primary IP of OpenVPN interface is not in the config
         * Must query the openVPN node
         */
        if (intfConf.getInterfaceId() == 250) {
            OpenVpn openvpn = (OpenVpn) UvmContextFactory.context().nodeManager().node("untangle-node-openvpn");
            if (openvpn == null) {
                logger.warn("OpenVPN node not found");
                return null;
            }
            
            IPAddress addr = openvpn.getVpnServerAddress().getIp();
            if (addr == null) {
                logger.warn("VPN Server address not found");
                return null;
            }

            return addr.getAddr();
        }

        IPNetwork network = intfConf.getPrimaryAddress();

        if ( network == null ) {
            logger.warn("No primary address for: " + intfConf.getName());
            return null;
        }

        IPAddress address = network.getNetwork();

        if ( address == null ) {
            logger.warn("No address for: " + network);
            return null;
        }

        return address.getAddr();
    }

    /* Listener functions */
    private void callNetworkListeners()
    {
        logger.debug( "Calling network listeners." );
        for ( NetworkConfigurationListener listener : this.networkListeners ) {
            if ( logger.isDebugEnabled()) logger.debug( "Calling listener: " + listener );

            try {
                listener.event( this.networkConfiguration );
            } catch ( Exception e ) {
                logger.error( "Exception calling listener", e );
            }
        }
        logger.debug( "Done calling network listeners." );
    }

    public void registerListener( NetworkConfigurationListener networkListener )
    {
        this.networkListeners.add( networkListener );
    }

    public void unregisterListener( NetworkConfigurationListener networkListener )
    {
        this.networkListeners.remove( networkListener );
    }

    private void initPriv() throws Exception
    {
    }

    /* Load the network configuration */
    private NetworkConfiguration loadNetworkConfiguration()
    {
        return null;
//         SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
//         NetworkConfiguration settings = null;
        
//         try {
//             settings = settingsManager.load(NetworkConfiguration.class, "/etc/untangle-net-alpaca/netConfig");
//         } catch (SettingsManager.SettingsException e) {
//             logger.error("Unable to read netConfig file: ", e );
//             return null;
//         }

//         if (settings == null) {
//             logger.error("Failed to read network settings");
//         } 
        
//         return settings;
    }
}
