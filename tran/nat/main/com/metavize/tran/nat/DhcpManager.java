/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.nat;

import java.util.List;
import java.util.Iterator;

import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;

import org.apache.log4j.Logger;

import com.metavize.mvvm.NetworkingManager;
import com.metavize.mvvm.NetworkingConfiguration;
import com.metavize.mvvm.MvvmContextFactory;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.firewall.MACAddress;

import com.metavize.mvvm.tran.TransformStartException;
import com.metavize.mvvm.tran.TransformException;

class DhcpManager
{
    private static final DhcpManager INSTANCE = new DhcpManager();

    private static final String COMMENT = "#";
    private static final String HEADER  = COMMENT + " AUTOGENERATED BY METAVIZE DO NOT MODIFY MANUALLY\n\n";

    private static final String FLAG_DHCP_RANGE       = "dhcp-range";
    private static final String FLAG_DHCP_HOST        = "dhcp-host";
    private static final String FLAG_DHCP_OPTION      = "dhcp-option";
    private static final String FLAG_DHCP_GATEWAY     = "3";
    private static final String FLAG_DHCP_SUBNET      = "1";
    private static final String FLAG_DHCP_NAMESERVERS = "6";
    private static final String FLAG_DNS_LISTEN       = "listen-address";

    private static final String DNS_MASQ_FILE         = "/etc/dnsmasq.conf";
    private static final String DNS_MASQ_CMD          = "/etc/init.d/dnsmasq ";
    private static final String DNS_MASQ_CMD_RESTART  = DNS_MASQ_CMD + " restart";
    private static final String DNS_MASQ_CMD_STOP     = DNS_MASQ_CMD + " stop";

    private final Logger logger = Logger.getLogger( DhcpManager.class );
    
    private DhcpManager()
    {
    }

    void configure( NatSettings settings ) throws TransformStartException
    {
        int code;

        /* Have to stop then start dnsmasq */
        try { 
            writeConfiguration( settings );
            
            logger.debug( "Reloading DNS Masq server" );

            Process p = Runtime.getRuntime().exec( DNS_MASQ_CMD_RESTART );
            code = p.waitFor();
        } catch ( Exception e ) {
            throw new TransformStartException( "Unable to reload DNS masq configuration", e );
        }

        if ( code != 0 ) {
            throw new TransformStartException( "Error starting DNS masq server" + code );
        }

        /* Enable/Disable DHCP forwarding  */
        try {
            if ( settings.getDhcpEnabled()) {
                MvvmContextFactory.context().argonManager().disableDhcpForwarding();
            } else {
                MvvmContextFactory.context().argonManager().enableDhcpForwarding();
            }
        } catch ( Exception e ) {
            throw new TransformStartException( "Error updating DHCP forwarding settings" + code );
        }
    }
    
    void deconfigure()
    {
        int code;

        try { 
            Process p = Runtime.getRuntime().exec( DNS_MASQ_CMD_STOP );
            code = p.waitFor();
            
            if ( code != 0 ) {
                logger.error( "Error stopping DNS masq server, returned code: " + code );
            }
        } catch ( Exception e ) {
            logger.error( "Error while stopping the DNS masq server", e );
        }

        /* Re-enable DHCP forwarding */
        try {
            logger.info( "Reenabling DHCP forwarding" );
            MvvmContextFactory.context().argonManager().enableDhcpForwarding();
        } catch ( Exception e ) {
            logger.error( "Error enabling DHCP forwarding", e );
        }
    }

    void loadLeases( NatSettings settings )
    {
        
    }

    private void parseLeaseFile( NatSettings settings )
    {
    }

    private void writeConfiguration( NatSettings settings )
    {
        StringBuilder sb = new StringBuilder();

        /* Need this to lookup the local IP address */
        NetworkingConfiguration netConfig = MvvmContextFactory.context().networkingManager().get();
        
        IPaddr externalAddress = netConfig.host();

        sb.append( HEADER );

        if ( settings.getDhcpEnabled()) {
            /* XXX Presently always defaulting leases to a fixed value */
            comment( sb, "DHCP Range:" );
            sb.append( FLAG_DHCP_RANGE + "=" + settings.getDhcpStartAddress().toString());
            sb.append( "," + settings.getDhcpEndAddress().toString() + ",4h\n\n\n" );
            
            /* Configure all of the hosts */
            List<DhcpLeaseRule> list = (List<DhcpLeaseRule>)settings.getDhcpLeaseList();
            
            if ( list != null ) {
                for ( Iterator<DhcpLeaseRule> iter = list.iterator() ; iter.hasNext() ; ) {
                    DhcpLeaseRule rule = iter.next();
                    
                    if ( !rule.getStaticAddress().isEmpty()) {
                        comment( sb, "Static DHCP Host" );
                        if ( rule.getResolvedByMac()) {
                            sb.append( FLAG_DHCP_HOST + "=" + rule.getMacAddress().toString());
                            sb.append( "," + rule.getStaticAddress().toString() + ",24h\n\n" );
                        } else {
                            sb.append( FLAG_DHCP_HOST + "=" + rule.getHostname());
                            sb.append( "," + rule.getStaticAddress().toString() + ",24h\n\n" );
                        }
                    }
                }
            }
            
            IPaddr gateway;
            IPaddr subnet;
            
            /* If Nat is turned on, use the settings from nat, otherwise use
             * the settings from DHCP */
            if ( settings.getNatEnabled()) {
                gateway = settings.getNatInternalAddress();
                subnet  = settings.getNatInternalSubnet();
            } else {
                gateway = settings.getDhcpGateway();
                subnet  = settings.getDhcpSubnet();
            }
            
            comment( sb, "Setting the gateway" );
            sb.append( FLAG_DHCP_OPTION + "=" + FLAG_DHCP_GATEWAY );
            sb.append( "," + gateway.toString() + "\n\n" );
            
            comment( sb, "Setting the subnet" );
            sb.append( FLAG_DHCP_OPTION + "=" + FLAG_DHCP_SUBNET );
            sb.append( "," + subnet.toString() + "\n\n" );
            
            
            appendNameServers( sb, settings, externalAddress );
        } else {
            comment( sb, "DHCP is disabled, not using a range or any host rules\n" );
        }
        
        if ( !settings.getDnsEnabled()) {
            comment( sb, "DNS is disabled, binding DNS to local host" );
            sb.append( FLAG_DNS_LISTEN + "=" + "127.0.0.1\n\n" );
        }

        /* XXX localdomain */
        writeFile( sb, DNS_MASQ_FILE );
    }

    static DhcpManager getInstance()
    {
        return INSTANCE;
    }

    /* XXX This should go into a global util class */
    private void writeFile( StringBuilder sb, String fileName ) 
    {
        BufferedWriter out = null;
        
        /* Open up the interfaces file */
        try {
            String data = sb.toString();
            
            out = new BufferedWriter(new FileWriter( fileName ));
            out.write( data, 0, data.length());
        } catch ( Exception ex ) {
            /* XXX May need to catch this exception, restore defaults
             * then try again */
            logger.error( "Error writing file " + fileName + ":", ex );
        }
        
        try {
            if ( out != null ) 
                out.close();
        } catch ( Exception ex ) {
            logger.error( "Unable to close file: " + fileName , ex );
        }
    }

    private void appendNameServers( StringBuilder sb, NatSettings settings, IPaddr externalAddress )
    {
        String nameservers = "";
        IPaddr tmp;
        
        if ( settings.getDnsEnabled()) {
            if ( settings.getNatEnabled()) {
                nameservers += settings.getNatInternalAddress().toString();
            } else {
                nameservers += externalAddress.toString();
            }
        }
        
        tmp = settings.getDhcpNameserver1();

        if ( tmp != null && !tmp.isEmpty()) {
            nameservers += ( nameservers.length() == 0 ) ? "" : ",";
            nameservers += tmp.toString();
        }

        tmp = settings.getDhcpNameserver2();
        
        if ( tmp != null && !tmp.isEmpty()) {
            nameservers += ( nameservers.length() == 0 ) ? "" : ",";
            nameservers += tmp.toString();
        }

        if ( nameservers.length() == 0 ) {
            comment( sb, "No nameservers specified\n" );
        } else {
            comment( sb, "Nameservers:" );
            sb.append( FLAG_DHCP_OPTION + "=" + FLAG_DHCP_NAMESERVERS );
            sb.append( "," + nameservers + "\n\n" );
        }
    }

    /* This guarantees the comment appears with a newline at the end */
    private void comment( StringBuilder sb, String comment ) {
        sb.append( COMMENT + " " + comment + "\n" );
    }

}
