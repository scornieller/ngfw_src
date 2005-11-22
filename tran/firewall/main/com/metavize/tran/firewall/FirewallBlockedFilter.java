/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: FirewallAccessEventHandler.java 3373 2005-11-12 00:13:48Z amread $
 */

package com.metavize.tran.firewall;

import com.metavize.mvvm.logging.SimpleEventFilter;
import com.metavize.mvvm.logging.RepositoryDesc;

public class FirewallBlockedFilter implements SimpleEventFilter<FirewallEvent>
{
    private static final RepositoryDesc REPO_DESC = new RepositoryDesc("Firewall Block Events");

    private static final String WARM_QUERY
        = "FROM FirewallEvent evt WHERE evt.wasBlocked = true AND evt.pipelineEndpoints.policy = :policy ORDER BY evt.timeStamp";

    // SimpleEventFilter methods ----------------------------------------------

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public String[] getQueries()
    {
        return new String[] { WARM_QUERY };
    }

    public boolean accept(FirewallEvent e)
    {
        if (e instanceof FirewallEvent) {
            FirewallEvent re = (FirewallEvent)e;
            return re.getWasBlocked();
        } else {
            return false;
        }
    }
}
