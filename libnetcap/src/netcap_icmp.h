/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
#ifndef __NETCAP_ICMP_H_
#define __NETCAP_ICMP_H_

#include "libnetcap.h"

int  netcap_icmp_init();
int  netcap_icmp_cleanup();
int  netcap_icmp_send (char *data, int data_len, netcap_pkt_t* pkt);
int  netcap_icmp_call_hook( netcap_pkt_t* pkt );
void netcap_icmp_null_hook( netcap_session_t* netcap_sess, netcap_pkt_t* pkt, void* arg);

#endif
