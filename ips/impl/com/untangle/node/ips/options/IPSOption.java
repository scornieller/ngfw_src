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

package com.untangle.node.ips.options;

import java.lang.reflect.*;
import java.util.regex.PatternSyntaxException;

import com.untangle.node.ips.IPSDetectionEngine;
import com.untangle.node.ips.IPSRuleSignatureImpl;
import com.untangle.node.ips.IPSSessionInfo;
import com.untangle.uvm.vnet.event.*;
import org.apache.log4j.Logger;

public abstract class IPSOption
{
    protected final IPSRuleSignatureImpl signature;
    protected boolean negationFlag = false;

    private static final Logger log = Logger.getLogger(IPSOption.class);

    protected IPSOption(IPSRuleSignatureImpl signature, String params)
    {
        this.signature = signature;
    }

    // Overriden in concrete children that are runnable
    public boolean runnable()
    {
        return false;
    }

    // Overriden in concrete children that are runnable
    public boolean run(IPSSessionInfo sessionInfo)
    {
        return true;
    }

    public static IPSOption buildOption(IPSDetectionEngine engine,
                                        IPSRuleSignatureImpl signature,
                                        String optionName,
                                        String params,
                                        boolean initializeSettingsTime)
    {
        boolean flag = false;
        if(params.charAt(0) == '!')  {
            flag = true;
            params = params.replaceFirst("!","").trim();
        }

        if(params.charAt(0) == '\"' && params.charAt(params.length()-1) == '\"')
            params = params.substring(1,params.length()-1);

        // XXX get rid of this reflection

        IPSOption option = null;
        Class optionDefinition;
        Class[] fourArgsClass = new Class[] { IPSDetectionEngine.class, IPSRuleSignatureImpl.class, String.class, Boolean.TYPE };
        Object[] fourOptionArgs = new Object[] { engine, signature, params, initializeSettingsTime };
        Class[] threeArgsClass = new Class[] { IPSRuleSignatureImpl.class, String.class, Boolean.TYPE };
        Object[] threeOptionArgs = new Object[] { signature, params, initializeSettingsTime };
        Class[] twoArgsClass = new Class[] { IPSRuleSignatureImpl.class, String.class };
        Object[] twoOptionArgs = new Object[] { signature, params };
        Constructor optionConstructor;

        optionName = optionName.toLowerCase();
        char ch = optionName.charAt(0);
        try {
            optionName = optionName.replaceFirst(""+ch,""+(char)(ch - 'a' + 'A'));
        } catch(PatternSyntaxException e) {
            log.error("Bad option name", e);
        }

        try {
            // First look for a three arg one, then the two arg one
            // (since most don't care about initializeSettingsTime).
            optionDefinition = Class.forName("com.untangle.node.ips.options."+optionName+"Option");

            // XXX remove reflection
            try {
                optionConstructor = optionDefinition.getConstructor(fourArgsClass);
                option = (IPSOption) createObject(optionConstructor, fourOptionArgs);
            } catch (NoSuchMethodException exn) {
                try {
                    optionConstructor = optionDefinition.getConstructor(threeArgsClass);
                    option = (IPSOption) createObject(optionConstructor, threeOptionArgs);
                } catch (NoSuchMethodException e) {
                    optionConstructor = optionDefinition.getConstructor(twoArgsClass);
                    option = (IPSOption) createObject(optionConstructor, twoOptionArgs);
                }
            }
            if (option != null) {
                option.negationFlag = flag;
            }
        } catch (ClassNotFoundException e) {
            log.info("Could not load option(ClassNotFound): " + optionName + ", ignoring rule: " + signature.rule().getText());
            signature.remove(true);
        } catch (NoSuchMethodException e) {
            log.error("Could not load option(NoSuchMethod): ", e);
        }
        return option;
    }

    public boolean optEquals(IPSOption o)
    {
        return negationFlag == o.negationFlag;
    }

    public int optHashCode()
    {
        return 17 * 37 + (negationFlag ? 1 : 0);
    }

    private static Object createObject(Constructor constructor,
                                       Object[] arguments)
    {
        Object object = null;
        try {
            object = constructor.newInstance(arguments);
        } catch (InstantiationException e) {
            log.error("Could not create object(InstantiationException): ", e);
        } catch (IllegalAccessException e) {
            log.error("Could not create object(IllegalAccessException): ", e);
        } catch (IllegalArgumentException e) {
            log.error("Could not create object(IllegalArgumentException): ", e);
        } catch (InvocationTargetException e) {
            log.error("Could not create object(InvocationTargetException): ", e.getTargetException());
        }
        return object;
    }
}

