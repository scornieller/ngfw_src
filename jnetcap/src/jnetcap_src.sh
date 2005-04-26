#!/bin/zsh

# Copyright (c) 2003 Metavize Inc.
# All rights reserved.
# This software is the confidential and proprietary information of
# Metavize Inc. ("Confidential Information").  You shall
# not disclose such Confidential Information.

# $Id$

## first: element = c name or if no second, c and java name
## second: java name
packages=( "Netcap" "Subscription" "SubscriptionGen SubscriptionGenerator" "Session NetcapSession"
    "UDPSession NetcapUDPSession" "TCPSession NetcapTCPSession" "InterfaceSet" "IPTraffic" "Shield" "ICMPTraffic" )
package=$1

c_package=${package//./_}
j_package=${package//./\/}

header='
/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

#ifndef __JNETCAP_H_
#define __JNETCAP_H_

/* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!  *
 * This file is autogenerated, do not edit manually, edit jnetcap_src.sh instead  *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!  */

#define _UNINITIALIZED  0xDEADD00D
#define _INITIALIZED    ~_UNINITIALIZED

#define JLONG_TO_UINT( j_long )  ((unsigned long)(j_long) & 0xFFFFFFFF)
#define UINT_TO_JLONG( num )     ((jlong)((uint)(num)))

extern __thread JNIEnv* thread_env;
JNIEnv* jnetcap_get_java_env( void );

/* Returns INITIALIZED if netcap is unitialized, and unitialized otherwise */
int jnetcap_initialized( void );

// JN Build standard name eg com_metavize_jnetcap_Class_name
// JF Build function name eg Java_com_metavize_jnetcap_Class_name
// JP Build path
#define JN_BUILD_NAME(CLASS,NAME)  '${c_package}'_ ## CLASS ## _ ## NAME
#define JF_BUILD_NAME(CLASS,FUNC)  Java_ ## '${c_package}'_ ## CLASS ## _ ## FUNC
#define JP_BUILD_NAME(OBJ)         "'$j_package'/" #OBJ
'

footer="
#endif  // __JNETCAP_H_
"

page=""

generateRule() 
{
    c_name=$1
    j_name=$2
    
    if [[ "$j_name" == "" ]]; then
        j_name=$c_name
    fi
    
    page=$page"
#define JH_${c_name}       \"${c_package}_${j_name}.h\"
#define JN_${c_name}(VAL)  JN_BUILD_NAME( ${j_name}, VAL )
#define JF_${c_name}(FUNC) JF_BUILD_NAME( ${j_name}, FUNC )
"
}

buildPage() 
{
    page=$header
    
    for package in $packages; do
        generateRule ${=${package}}
    done
    
    page="$page $footer"
}

buildPage

echo $page
