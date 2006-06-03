/*
 * Copyright (c) 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.portal;

import java.util.List;

import com.metavize.mvvm.portal.Application;
import com.metavize.mvvm.portal.PortalSettings;
import com.metavize.mvvm.logging.EventManager;
import com.metavize.mvvm.logging.LogEvent;

public interface PortalTransform
{
    List<Application> getApplications();

    List<String> getApplicationNames();

    Application getApplication(String name);

    PortalSettings getPortalSettings();

    void setPortalSettings(PortalSettings settings);

    EventManager<LogEvent> getEventManager();
}

