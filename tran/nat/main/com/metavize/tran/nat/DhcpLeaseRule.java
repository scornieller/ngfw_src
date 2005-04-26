/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: MimeTypeRule.java 229 2005-04-07 22:25:00Z amread $
 */

package com.metavize.tran.nat;

import java.util.Date;

import com.metavize.mvvm.tran.Rule;

import com.metavize.mvvm.tran.IPNullAddr;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.firewall.MACAddress;


/**
 * Rule for storing DHCP leases.
 *
 * @author <a href="mailto:rbscott@metavize.com">Robert Scott</a>
 * @version 1.0
 * @hibernate.class
 * table="DHCP_LEASE_RULE"
 */
public class DhcpLeaseRule extends Rule
{
    private MACAddress macAddress;
    private String     hostname;
    private IPNullAddr currentAddress;
    private IPNullAddr staticAddress;
    private Date       endOfLease;
    private boolean    resolvedByMac = true;

    // Constructors 
    /**
     * Hibernate constructor 
     */
    public DhcpLeaseRule()
    {
    }

    public DhcpLeaseRule( MACAddress macAddress, String hostname, IPNullAddr currentAddress,
                          IPNullAddr staticAddress, Date endOfLease, boolean resolvedByMac )
    {
        this.macAddress     = macAddress;
        this.hostname       = hostname;
        this.currentAddress = currentAddress;
        this.staticAddress  = staticAddress;
        this.endOfLease     = endOfLease;
        this.resolvedByMac  = resolvedByMac;
    }

    /**
     * MAC address
     *
     * @return the mac address.
     * @hibernate.property
     * type="com.metavize.mvvm.type.firewall.MACAddressUserType"
     * @hibernate.column
     * name="MAC_ADDRESS"
     */
    public MACAddress getMacAddress()
    {
        return macAddress;
    }

    public void setMacAddress( MACAddress macAddress )
    {
        this.macAddress = macAddress;
    }
    
    /**
     * Host name
     *
     * @return the desired/assigned host name for this machine.
     * @hibernate.property
     * @hibernate.column
     * name="HOSTNAME"
     */
    public String getHostname()
    {
        return hostname;
    }

    public void setHostname( String hostname )
    {
        this.hostname = hostname;
    }

    /**
     * Get currrent IP address for this MAC address
     *
     * @return current address.
     * @hibernate.property
     * type="com.metavize.mvvm.type.IPNullAddrUserType"
     * @hibernate.column
     * name="CURRENT_ADDRESS"
     * sql-type="inet"
     */
    public IPNullAddr getCurrentAddress()
    {
        return this.currentAddress;
    }
    
    public void setCurrentAddress( IPNullAddr currentAddress ) 
    {
        this.currentAddress = currentAddress;
    }

    /**
     * Get static IP address for this MAC address
     *
     * @return desired static address.
     * @hibernate.property
     * type="com.metavize.mvvm.type.IPNullAddrUserType"
     * @hibernate.column
     * name="STATIC_ADDRESS"
     * sql-type="inet"
     */
    public IPNullAddr getStaticAddress()
    {
        return this.staticAddress;
    }
    
    public void setStaticAddress( IPNullAddr staticAddress ) 
    {
        this.staticAddress = staticAddress;
    }
    
    /**
     * End of lease time
     *
     * @return The current end of the lease for this object.
     * @hibernate.property
     * @hibernate.column
     * name="END_OF_LEASE"
     */
    public Date getEndOfLease()
    {
        return endOfLease;
    }

    public void setEndOfLease( Date endOfLease )
    {
        this.endOfLease = endOfLease;
    }

    /**
     * Resolve by MAC
     *
     * @return true if the MAC address is used to resolve this rule.
     * @hibernate.property
     * @hibernate.column
     * name="IS_RESOLVE_MAC"
     */
    public boolean getResolvedByMac()
    {
        return resolvedByMac;
    }

    public void setResolvedByMac( boolean resolvedByMac )
    {
        this.resolvedByMac = resolvedByMac;
    }    
}
