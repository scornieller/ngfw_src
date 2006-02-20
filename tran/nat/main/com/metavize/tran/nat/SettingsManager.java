/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.nat;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

import org.apache.log4j.Logger;

import com.metavize.mvvm.IntfConstants;

import com.metavize.mvvm.argon.IntfConverter;

import com.metavize.mvvm.networking.SetupState;
import com.metavize.mvvm.networking.Interface;
import com.metavize.mvvm.networking.NetworkSpace;
import com.metavize.mvvm.networking.DhcpLeaseRule;
import com.metavize.mvvm.networking.DnsStaticHostRule;
import com.metavize.mvvm.networking.RedirectRule;
import com.metavize.mvvm.networking.IPNetwork;
import com.metavize.mvvm.networking.IPNetworkRule;
import com.metavize.mvvm.networking.NetworkSpacesSettings;
import com.metavize.mvvm.networking.NetworkSpace;
import com.metavize.mvvm.networking.NetworkSpacesSettingsImpl;
import com.metavize.mvvm.networking.ServicesSettings;
import com.metavize.mvvm.networking.ServicesSettingsImpl;
import com.metavize.mvvm.networking.EthernetMedia;
import com.metavize.mvvm.networking.NetworkUtil;

import com.metavize.mvvm.networking.internal.DhcpLeaseInternal;
import com.metavize.mvvm.networking.internal.DnsStaticHostInternal;
import com.metavize.mvvm.networking.internal.NetworkSpacesInternalSettings;
import com.metavize.mvvm.networking.internal.NetworkSpaceInternal;
import com.metavize.mvvm.networking.internal.RedirectInternal;
import com.metavize.mvvm.networking.internal.ServicesInternalSettings;

import com.metavize.mvvm.security.Tid;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.ValidateException;

import com.metavize.mvvm.tran.firewall.ip.IPMatcher;
import com.metavize.mvvm.tran.firewall.ip.IPMatcherFactory;
import com.metavize.mvvm.tran.firewall.intf.IntfMatcher;
import com.metavize.mvvm.tran.firewall.intf.IntfMatcherFactory;
import com.metavize.mvvm.tran.firewall.port.PortMatcher;
import com.metavize.mvvm.tran.firewall.port.PortMatcherFactory;
import com.metavize.mvvm.tran.firewall.ProtocolMatcher;

class SettingsManager
{
    private final Logger logger = Logger.getLogger( SettingsManager.class );

    SettingsManager()
    {
    }

    /* The network settings are the current settings.  The NetworkingManager always
     * returns a copy, so it is safe to mess around with this object */
    NetworkSpacesSettings toNetworkSettings( NetworkSpacesSettings networkSettings, 
                                             NatBasicSettings natSettings )
    {
        ((NetworkSpacesSettingsImpl)networkSettings).setSetupState( SetupState.BASIC );        
        List<NetworkSpace> networkSpaceList = networkSettings.getNetworkSpaceList();

        NetworkSpace primary;
        NetworkSpace natSpace;
        
        /* Assuming there is at least one network space */
        primary =networkSpaceList.get( 0 );
        
        /* Just ignore the previous settings */
        natSpace = new NetworkSpace();
        natSpace.setName( NetworkUtil.DEFAULT_SPACE_NAME_NAT );
        
        boolean isNatEnabled = natSettings.getNatEnabled();
        natSpace.setLive( isNatEnabled );
        List<IPNetworkRule> networkList = new LinkedList<IPNetworkRule>();
            
        networkList.add( IPNetworkRule.makeInstance( natSettings.getNatInternalAddress(),
                                                     natSettings.getNatInternalSubnet()));
        natSpace.setNetworkList( networkList );
        natSpace.setIsDhcpEnabled( false );
        natSpace.setIsTrafficForwarded( true );
        natSpace.setIsNatEnabled( true );
        natSpace.setNatSpace( primary );
        natSpace.setNatAddress( null );
        
        /* DMZ is disabled on this space */
        natSpace.setIsDmzHostEnabled( false );
        natSpace.setIsDmzHostLoggingEnabled( false );
        natSpace.setDmzHost( null );
        
        /* DMZ settings are registered against the primary space */
        primary.setIsDmzHostEnabled( natSettings.getDmzEnabled());
        primary.setIsDmzHostLoggingEnabled( natSettings.getDmzLoggingEnabled());
        primary.setDmzHost( natSettings.getDmzAddress());
        
        /* set the list of network spaces */
        networkSpaceList.clear();
        networkSpaceList.add( primary );
        networkSpaceList.add( natSpace );
        networkSettings.setNetworkSpaceList( networkSpaceList );

        /* The mtu is not configurable from this panel */
        primary.setMtu( primary.DEFAULT_MTU );
        natSpace.setMtu( natSpace.DEFAULT_MTU );

        /* Setup the interfaces */
        List<Interface> interfaceList = networkSettings.getInterfaceList();

        /* Move the internal interface into the secondary network space */
        boolean foundInternal = false;
        for ( Interface intf : interfaceList ) {
            intf.setNetworkSpace( primary );

            if ( intf.getArgonIntf() == IntfConstants.INTERNAL_INTF ) {
                foundInternal = true;
                if ( isNatEnabled ) intf.setNetworkSpace( natSpace );
            };
        }
        
        if ( !foundInternal ) {
            /* XXXX This is error code, but it should probably try to retain some of the interface
             * settings XXX */
            logger.error( "The interface list did not contain the internal " + 
                          "interface, creating a new interface list" );
            
            interfaceList = new LinkedList<Interface>();

            byte argonIntfArray[] = IntfConverter.getInstance().argonIntfArray();
            Arrays.sort( argonIntfArray );
            for ( byte argonIntf : argonIntfArray ) {
                /* The VPN interface doesn't belong to a network space */
                if ( argonIntf == IntfConstants.VPN_INTF ) continue;
                
                /* Add each interface to the list */
                Interface intf =  new Interface( argonIntf, EthernetMedia.AUTO_NEGOTIATE, true );
                intf.setName( IntfConstants.toName( argonIntf ));
                if ( isNatEnabled && ( argonIntf == IntfConstants.INTERNAL_INTF )) {
                    intf.setNetworkSpace( natSpace );
                } else {
                    intf.setNetworkSpace( primary );
                }
                
                interfaceList.add( intf );
            }
        }

        networkSettings.setInterfaceList( interfaceList );

        /* Set the redirects */
        networkSettings.setRedirectList( natSettings.getRedirectList());
        
        return networkSettings;
    }

    NetworkSpacesSettings toNetworkSettings( NetworkSpacesSettings networkSettings, 
                                             NatAdvancedSettings advanced )
        throws ValidateException
    {

        logger.debug( "New advanced settings: " + advanced );

        ((NetworkSpacesSettingsImpl)networkSettings).setSetupState( SetupState.ADVANCED );
        /* Is enabled should have already been set */
        // networkSettings.setIEnabled();
        
        /* Fix all of the links to NetworkSpaces */
        List<NetworkSpace> networkSpaceList = advanced.getNetworkSpaceList();

        /* Replace the primary network space */
        NetworkSpace primary = networkSpaceList.get( 0 );

        NetworkSpace previousPrimary = networkSettings.getNetworkSpaceList().get( 0 );
        
        /* MTU is the only value that carries over */
        previousPrimary.setMtu( primary.getMtu());
        previousPrimary.setName( primary.getName());
        previousPrimary.setIsTrafficForwarded( primary.getIsTrafficForwarded());

        /* Swap out the primary network space */
        networkSpaceList.remove( 0 );
        networkSpaceList.add( 0, previousPrimary );
        
        Map<Long,NetworkSpace> networkSpaceMap = new HashMap<Long,NetworkSpace>();

        for( NetworkSpace space : networkSpaceList ) networkSpaceMap.put( space.getBusinessPapers(), space );

        List<Interface> interfaceList = advanced.getInterfaceList();

        for( Interface intf : interfaceList ) {
            String name = intf.getName();
            if ( intf.getNetworkSpace() == null ) {
                throw new ValidateException( "Interface " + name + " has an empty network space" );
            }
            
            NetworkSpace space = networkSpaceMap.get( intf.getNetworkSpace().getBusinessPapers());
            /* This shouldn't happen */
            if ( space == null ) {
                throw new ValidateException( "Interface " + name + " is not assigned a network space" );
            }
            
            logger.debug( "stitching the interface to " + space.hashCode() +  " " + space.getBusinessPapers());
            intf.setNetworkSpace( space );
            space = intf.getNetworkSpace();
            logger.debug( "interface -> " + space.hashCode() +  " " + space.getBusinessPapers());
        }

        for ( NetworkSpace space : networkSpaceList ) {
            NetworkSpace natSpace = space.getNatSpace();
            if ( natSpace != null ) {
                natSpace = networkSpaceMap.get( natSpace.getBusinessPapers());
                /* if this happens there is nothing the user can do. */
                if ( natSpace == null ) {
                    throw new ValidateException( "Network space " + space.getName() + " is unassigned" );
                }
            } else if ( !space.getIsPrimary()) {
                logger.warn( "Network space: " + space.getName() + " has a null nat space" );
            }
        }
        
        networkSettings.setInterfaceList( interfaceList );

        for( Interface intf : interfaceList ) {
            NetworkSpace space = intf.getNetworkSpace();
            logger.debug( "interface -> " + space.hashCode() +  " " + space.getBusinessPapers());
        }

        networkSettings.setNetworkSpaceList( networkSpaceList );
        networkSettings.setRoutingTable( advanced.getRoutingTable());
        networkSettings.setRedirectList( advanced.getRedirectList());
        
        /* Everything else is not configurable here. */
        return networkSettings;
    }


    NatAdvancedSettings toAdvancedSettings( NetworkSpacesSettings network,
                                            ServicesInternalSettings services )
    {
        network.getNetworkSpaceList().get( 0 ).setIsPrimary( true );
        return new NatAdvancedSettingsImpl( network, new ServicesSettingsImpl( services ));
    }

    NatBasicSettings toBasicSettings( Tid tid,
                                      NetworkSpacesInternalSettings networkSettings,
                                      ServicesInternalSettings servicesSettings )
    {
        NatBasicSettings natSettings = new NatSettingsImpl( tid, SetupState.BASIC );
                
        List<NetworkSpaceInternal> networkSpaceList = networkSettings.getNetworkSpaceList();
        
        /* Get the network space list in order to determine how many spaces there are */
        if ( networkSpaceList.size() == 1 ) {
            /* Use this for the dmz */
            NetworkSpaceInternal networkSpace = networkSpaceList.get( 0 );
            
            /* Nat is disabled */
            natSettings.setNatEnabled( false );
            natSettings.setNatInternalAddress( NatUtil.DEFAULT_NAT_ADDRESS );
            natSettings.setNatInternalSubnet( NatUtil.DEFAULT_NAT_NETMASK );                
        } else if ( networkSpaceList.size() > 1 ) {
            NetworkSpaceInternal networkSpace = networkSpaceList.get( 1 );
            
            natSettings.setNatEnabled( networkSpace.getIsEnabled());
            IPNetwork primary = networkSpace.getPrimaryAddress();
            natSettings.setNatInternalAddress( primary.getNetwork());
            natSettings.setNatInternalSubnet( primary.getNetmask());                
        } else {
            logger.error( "No network spaces, returning default settings" );
            natSettings = getDefaultSettings( tid );
        }
        
        /* DMZ settings (DMZ settings are registered against the primary space) */
        setupDmz( natSettings, networkSpaceList.get( 0 ));

        /* Setup the services settings */
        
        /* dhcp settings */
        if ( servicesSettings == null ) {
            /* Default services settings */
            natSettings.setDhcpEnabled( true );
            natSettings.setDhcpStartAddress( NatUtil.DEFAULT_DHCP_START );
            natSettings.setDhcpEndAddress( NatUtil.DEFAULT_DHCP_END );
            natSettings.setDhcpLeaseTime( NatUtil.DEFAULT_LEASE_TIME_SEC );
            natSettings.setDhcpLeaseList( new LinkedList<DhcpLeaseRule>());
            
            natSettings.setDnsEnabled( true );
            natSettings.setDnsLocalDomain( null );
            natSettings.setDnsStaticHostList( new LinkedList<DnsStaticHostRule>());
        } else {
            natSettings.setDhcpEnabled( servicesSettings.getIsDhcpEnabled());
            natSettings.setDhcpStartAddress( servicesSettings.getDhcpStartAddress());
            natSettings.setDhcpEndAddress( servicesSettings.getDhcpEndAddress());
            natSettings.setDhcpLeaseTime( servicesSettings.getDhcpLeaseTime());
            natSettings.setDhcpLeaseList( servicesSettings.getDhcpLeaseRuleList());
            
            /* dns settings */
            natSettings.setDnsEnabled( servicesSettings.getIsDnsEnabled());
            natSettings.setDnsLocalDomain( servicesSettings.getDnsLocalDomain());
            natSettings.setDnsStaticHostList( servicesSettings.getDnsStaticHostRuleList());
        }
        
        /* Setup the redirect settings */
        natSettings.setRedirectList( networkSettings.getRedirectRuleList());
                
        return natSettings;
    }

    /** Convert a set of network settings from basic mode to advanced mode */
    /* This recycles network, but that is okay since the network manager always *
     * returns a copy */
    NetworkSpacesSettings basicToAdvanced( NetworkSpacesSettingsImpl ns )
    {
        /* Update the setup state */
        ns.setSetupState( SetupState.ADVANCED );
        
        /* use the interface out, because the interface has generics and the impl doesn't */
        NetworkSpacesSettings network = ns;

        NetworkSpace primary = ns.getNetworkSpaceList().get( 0 );
        
        if ( primary.getIsDmzHostEnabled() && ( primary.getNetworkList().size() > 0 )) {
            
            IPNetworkRule primaryNetwork = (IPNetworkRule)primary.getNetworkList().get( 0 );
            IPaddr local = primaryNetwork.getNetwork();
            
            IntfMatcherFactory imf  = IntfMatcherFactory.getInstance();
            IPMatcherFactory   ipmf = IPMatcherFactory.getInstance();
            PortMatcherFactory pmf  = PortMatcherFactory.getInstance();
            
            RedirectRule dmz = new RedirectRule( true, ProtocolMatcher.MATCHER_ALL,
                                                 imf.getExternalMatcher(), imf.getAllMatcher(),
                                                 ipmf.getAllMatcher(), ipmf.makeSingleMatcher( local ),
                                                 pmf.getAllMatcher(), pmf.getAllMatcher(),
                                                 true, primary.getDmzHost(), -1 );

            /* Add the dmz to the end of the redirect list */
            network.getRedirectList().add( dmz );
        }
        
        /* Disable dmz host, it may/may not have been replaced by a redirect rule */
        primary.setIsDmzHostEnabled( false );
        
        return network;
    }

    /** Convert a set of network settings from basic mode to advanced mode */
    /* This recycles network, but that is okay since the network manager always *
     * returns a copy */
    NetworkSpacesSettings resetToBasic( Tid tid, NetworkSpacesSettingsImpl ns )
    {
        /* Reset to the default settings */
        return toNetworkSettings( ns, getDefaultSettings( tid ));
    }

    private void setupDmz( NatBasicSettings settings, NetworkSpaceInternal space )
    {
        settings.setDmzEnabled( space.getIsDmzHostEnabled());
        settings.setDmzLoggingEnabled( space.getIsDmzHostLoggingEnabled());
        IPaddr dmz = space.getDmzHost();
        if ( dmz == null || dmz.isEmpty()) {
            /* Disable dmz, just so that it will save even if
             * it isn't in the corect network. */
            settings.setDmzEnabled( false );
            settings.setDmzAddress( NatUtil.DEFAULT_DMZ_ADDRESS );
        } else {
            settings.setDmzAddress( dmz );
        }
    }

    NatBasicSettings getDefaultSettings( Tid tid )
    {
        logger.info( "Using default settings" );

        NatSettingsImpl settings = new NatSettingsImpl( tid, SetupState.BASIC );

        List<RedirectRule> redirectList = new LinkedList<RedirectRule>();

        IntfMatcherFactory imf = IntfMatcherFactory.getInstance();
        IPMatcherFactory ipmf  = IPMatcherFactory.getInstance();
        PortMatcherFactory pmf = PortMatcherFactory.getInstance();

        try {
            settings.setNatEnabled( true );
            settings.setNatInternalAddress( NatUtil.DEFAULT_NAT_ADDRESS );
            settings.setNatInternalSubnet( NatUtil.DEFAULT_NAT_NETMASK );

            settings.setDmzLoggingEnabled( false );

            /* DMZ Settings */
            settings.setDmzEnabled( false );
            /* A sample DMZ */
            settings.setDmzAddress( NatUtil.DEFAULT_DMZ_ADDRESS );
            
            // !!!! Need the local matcher
//            RedirectRule tmp = new RedirectRule( false, ProtocolMatcher.MATCHER_ALL,
//                                                  imf.getExternalMatcher(), imf.getAllMatcher(),
//                                                  ipmf.getAllMatcher(), ipmf.getLocalMatcher(),
//                                                  pmf.getAllMatcher(), pmf.makeSingleMatcher( 8080 ),
//                                                  true, IPaddr.parse( "192.168.1.16" ), 80 );
//             tmp.setDescription( "Redirect incoming traffic to EdgeGuard port 8080 to port 80 on 192.168.1.16" );
//             tmp.setLog( true );
            
//             redirectList.add( tmp );

            RedirectRule tmp = new RedirectRule( false, ProtocolMatcher.MATCHER_ALL,
                                    imf.getExternalMatcher(), imf.getAllMatcher(),
                                    ipmf.getAllMatcher(), ipmf.getAllMatcher(),
                                    pmf.getAllMatcher(), pmf.makeRangeMatcher( 6000, 10000 ),
                                    true, (IPaddr)null, 6000 );
            tmp.setDescription( "Redirect incoming traffic from ports 6000-10000 to port 6000" );
            redirectList.add( tmp );

            tmp = new RedirectRule( false, ProtocolMatcher.MATCHER_ALL,
                                    imf.getInternalMatcher(), imf.getAllMatcher(),
                                    ipmf.getAllMatcher(), ipmf.parse( "1.2.3.4" ),
                                    pmf.getAllMatcher(), pmf.getAllMatcher(),
                                    true, IPaddr.parse( "4.3.2.1" ), 0 );
            tmp.setDescription( "Redirect outgoing traffic going to 1.2.3.4 to 4.2.3.1, (port is unchanged)" );
            tmp.setLog( true );

            redirectList.add( tmp );


            for ( RedirectRule redirect : redirectList ) redirect.setCategory( "[Sample]" );

            /* Enable DNS and DHCP */
            settings.setDnsEnabled( true );
            settings.setDhcpEnabled( true );

            settings.setDhcpStartAndEndAddress( NatUtil.DEFAULT_DHCP_START, NatUtil.DEFAULT_DHCP_END );
        } catch ( Exception e ) {
            logger.error( "This should never happen", e );
        }

        settings.setRedirectList( redirectList );

        return settings;
    }
}