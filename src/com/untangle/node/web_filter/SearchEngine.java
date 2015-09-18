/**
 * $Id: UrlRewriter.java 37269 2014-02-26 23:46:16Z dmorris $
 */
package com.untangle.node.web_filter;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.untangle.node.http.RequestLineToken;
import com.untangle.node.http.HeaderToken;
import com.untangle.uvm.util.UrlMatchingUtil;
import com.untangle.uvm.vnet.NodeTCPSession;

/**
 * Map search engine to their query URIs
 *
 */
public class SearchEngine
{
    private static final Logger logger = Logger.getLogger(UrlRewriter.class);

    private static final List<Pattern> searchEngines;
    static {
        searchEngines = new ArrayList<Pattern>();
        searchEngines.add(Pattern.compile(".*google\\.[a-z]+(\\.[a-z]+)?/search.*(\\?|&)q=([^&]+).*") );
        searchEngines.add(Pattern.compile(".*ask\\.[a-z]+(\\.[a-z]+)?/web.*(\\?|&)q=([^&]+).*") );
        searchEngines.add(Pattern.compile(".*bing\\.[a-z]+(\\.[a-z]+)?/search.*(\\?|&)q=([^&]+).*") );
        searchEngines.add(Pattern.compile(".*yahoo\\.[a-z]+(\\.[a-z]+)?/search.*(\\?|&)p=([^&]+).*") );
    };

    public static String getQueryTerm( InetAddress clientIp, RequestLineToken requestLine,  HeaderToken header )
    {
        URI uri = null;
        try {
            uri = new URI(requestLine.getRequestUri().normalize().toString().replaceAll("(?<!:)/+", "/"));
        } catch (URISyntaxException e) {
            logger.error("Could not parse URI '" + uri + "'", e);
        }

        String host = uri.getHost();
        if (null == host) {
            host = header.getValue("host");
            if (null == host) {
                host = clientIp.getHostAddress();
            }
        }
        host = UrlMatchingUtil.normalizeHostname(host);

        String url = host + uri.toString();

        logger.debug("getQueryTerms: trying to match string '" + url + "'");
        for (Pattern p : searchEngines ){
            logger.debug("getQueryTerms: ... with pattern '" + p.pattern() + "'");
            Matcher m = p.matcher( url );
            if( m.matches() ){
                logger.debug("getQueryTerms: ...... match !");
                String term = "";
                if( m.groupCount() >= 3 ){
                    term = m.group(3);
                }
                try{
                    term = URLDecoder.decode( term, "UTF-8" );
                } catch( Exception e) {

                }
                return term;
            }
        }
        return null;
    }

}
