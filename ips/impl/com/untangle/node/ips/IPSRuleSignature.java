/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.ips;

import com.untangle.node.ips.options.*;
import com.untangle.uvm.node.ParseException;
import com.untangle.uvm.vnet.event.*;

public class IPSRuleSignature
{
    private final IPSRuleSignatureImpl impl;

    // constructors ------------------------------------------------------------

    private IPSRuleSignature(IPSRuleSignatureImpl impl)
    {
        this.impl = impl;
    }

    // public static methods ---------------------------------------------------

    public static IPSRuleSignature parseSignature(IPSNodeImpl ips,
                                                  String signatureString,
                                                  int action, IPSRule rule,
                                                  boolean initSettingsTime,
                                                  String string)
        throws ParseException
    {
        IPSRuleSignatureImpl impl
            = new IPSRuleSignatureImpl(ips, signatureString, action, rule,
                                       initSettingsTime, string);

        return new IPSRuleSignature(impl);
    }

    public boolean remove()
    {
        return impl.remove();
    }

    public IPSRule rule()
    {
        return impl.rule();
    }

    public String getMessage()
    {
        return impl.getMessage();
    }

    public String getClassification()
    {
        return impl.getClassification();
    }

    public String getURL()
    {
        return impl.getURL();
    }

    public boolean execute(IPSNodeImpl ips, IPSSessionInfo info)
    {
        return impl.execute(ips, info);
    }

    public String toString()
    {
        return impl.toString();
    }

    static void dumpRuleTimes()
    {
        IPSRuleSignature.dumpRuleTimes();
    }

    // Object methods ----------------------------------------------------------

    public boolean equals(Object o)
    {
        if (!(o instanceof IPSRuleSignature)) {
            return false;
        }

        return impl.equals(((IPSRuleSignature)o).impl);
    }

    public int hashCode()
    {
        return impl.hashCode();
    }
}
