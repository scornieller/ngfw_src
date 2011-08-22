/*
 * $Id$
 */
package com.untangle.uvm.engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.message.MessageManager;
import com.untangle.uvm.toolbox.DownloadComplete;
import com.untangle.uvm.toolbox.DownloadProgress;
import com.untangle.uvm.toolbox.DownloadSummary;
import com.untangle.uvm.toolbox.InstallComplete;
import com.untangle.uvm.toolbox.PackageDesc;

/**
 * Tails apt output to produce progress messages for the Swing GUI.
 */
class AptLogTail implements Runnable
{
    private static final String APT_LOG = System.getProperty("uvm.log.dir") + "/apt.log";
    private static final long TIMEOUT = 30 * 60 * 1000;

    private static final Pattern FETCH_PATTERN;
    private static final Pattern DOWNLOAD_PATTERN;

    private final Logger logger = Logger.getLogger(getClass());

    static {
        FETCH_PATTERN = Pattern.compile(".*'(http://.*)' (.*\\.deb) ([0-9]+) (MD5Sum:|SHA1:|SHA256:)?([0-9a-z]+)");
        //6850K .......... .......... .......... .......... .......... 96 46.6K 6s
        DOWNLOAD_PATTERN = Pattern.compile(".* ([0-9]+)K[ .]+([0-9]+) *([0-9]+\\.*[0-9]+)K.*");
    }

    private final long key;
    private final PackageDesc requestingPackage;

    private final RandomAccessFile raf;

    private long lastActivity = -1;

    // constructor ------------------------------------------------------------

    AptLogTail(long key, PackageDesc requestingPackage)
    {
        logger.debug("AptLogTail(" + key + ")" + " new AptLogTail key: " + key);

        this.key = key;
        this.requestingPackage = requestingPackage;

        File f = new File(APT_LOG);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException exn) {
                throw new RuntimeException("could not create: " + APT_LOG);
            }
        }

        try {
            logger.debug("creating RandomAccessFile: " + f);
            raf = new RandomAccessFile(f, "r");
        } catch (FileNotFoundException exn) {
            throw new RuntimeException("should never happen");
        }

        logger.debug("AptLogTail(" + key + ")" + " constructed");
    }

    // package protected methods ----------------------------------------------

    long getKey()
    {
        return key;
    }

    // Runnable methods -------------------------------------------------------

    public void run()
    {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}
        
        logger.debug("AptLogTail(" + key + ")" + " tailing apt log");
        doIt();
        logger.debug("AptLogTail(" + key + ")" + " tailing apt log - done");

        try {
            raf.close();
        } catch (IOException exn) {
            logger.warn("could not close: " + APT_LOG);
        }
    }

    public void doIt()
    {
        MessageManager mm = LocalUvmContextFactory.context().messageManager();
        
        // find `start key'
        logger.debug("AptLogTail(" + key + ")" + " finding start key: \"start " + key + "\"");
        for (String line = readLine(); !line.contains("start " + key); line = readLine());

        // 'uri' package size hash
        List<PackageInfo> downloadQueue = new LinkedList<PackageInfo>();

        int totalSize = 0;
        while (true) {
            String line = readLine();

            Matcher m = FETCH_PATTERN.matcher(line);
            if (line.contains("END PACKAGE LIST")) {
                logger.debug("AptLogTail(" + key + ")" + " found: END PACKAGE LIST");
                break;
            } else if (m.matches()) {
                String url = m.group(1);
                String file = m.group(2);
                int size = new Integer(m.group(3));
                String hash = m.group(5);

                PackageInfo pi = new PackageInfo(url, file, size, hash);
                logger.debug("AptLogTail(" + key + ")" + " adding package: " + pi);
                downloadQueue.add(pi);
                totalSize += size;
            } else {
                logger.debug("AptLogTail(" + key + ")" + " does not match FETCH_PATTERN: " + line);
            }
        }

        if (isUpgrade() && downloadQueue.size() == 0) {
            // nothing to upgrade
            return;
        }

        logger.debug("AptLogTail(" + key + ")" + " Sending DownloadSummary(downloadQueue.size()=" + downloadQueue.size() + ", totalSize=" + totalSize + ")");
        mm.submitMessage(new DownloadSummary(downloadQueue.size(), totalSize, requestingPackage));

        for (PackageInfo pi : downloadQueue) {
            logger.debug("AptLogTail(" + key + ")" + " downloading: " + pi);
            while (true) {
                String line = readLine();
                Matcher m = DOWNLOAD_PATTERN.matcher(line);
                if (line.startsWith("DOWNLOAD SUCCEEDED: ")) {
                    logger.debug("AptLogTail(" + key + ")" + " Sending DownloadComplete");
                    mm.submitMessage(new DownloadComplete(true, requestingPackage));
                    break;
                } else if (line.startsWith("DOWNLOAD FAILED: " )) {
                    logger.debug("AptLogTail(" + key + ")" + " Sending DownloadComplete (failed)");
                    mm.submitMessage(new DownloadComplete(false, requestingPackage));
                    break;
                } else if (m.matches()) {
                    int bytesDownloaded = Integer.parseInt(m.group(1)) * 1000;
                    String speed = m.group(3);

                    // enqueue event
                    DownloadProgress dpe;
                    if (null == requestingPackage) {
                        dpe = new DownloadProgress(pi.file, bytesDownloaded, pi.size, speed, null);
                    } else {
                        pi.bytesDownloaded = bytesDownloaded;

                        int soFar = 0;
                        for (PackageInfo ppi : downloadQueue) {
                            soFar += ppi.bytesDownloaded;
                        }

                        dpe = new DownloadProgress(pi.file, soFar, totalSize, speed, requestingPackage);
                    }

                    logger.debug("AptLogTail(" + key + ")" + " Sending DownloadProgress:" + dpe);
                    mm.submitMessage(dpe);
                } else {
                    logger.debug("AptLogTail(" + key + ")" + " ignoring line: " + line.substring(0,(line.length()<10 ? line.length() : 9)) + "...");
                }
            }
        }

        logger.debug("AptLogTail(" + key + ")" + " Sending InstallComplete");
        mm.submitMessage(new InstallComplete(true, requestingPackage));
    }

    private String readLine()
    {
        try {
            while (true) {
                long t = System.currentTimeMillis();
                if (0 > lastActivity) {
                    lastActivity = t;
                }
                String line = raf.readLine();

                if (line == null) {
                    try {
                        if (TIMEOUT < t - lastActivity) {
                            // just end the thread adding TimeoutEvent
                            logger.warn("AptLogTail timing out: " + (t - lastActivity));
                            throw new RuntimeException("timing out: " + (t - lastActivity));
                        } else {
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException exn) { }
                } else {
                    lastActivity = t;

                    /**
                     * rsyslog inserts the date at the beginning followed by ": "
                     * find that and cut out the date
                     */
                    int index = line.indexOf(": ");
                    if (index > 0) 
                        return line.substring(index+1);
                    else
                        return line;
                } 
            }
        } catch (IOException exn) {
            logger.warn("could not read apt.log", exn);
            throw new RuntimeException("could not read apt-log", exn);
        } catch (Exception exn) {
            logger.warn("could not read apt.log", exn);
            throw new RuntimeException("could not read apt-log", exn);
        }
    }

    private boolean isUpgrade()
    {
        return null == requestingPackage;
    }

    private static class PackageInfo
    {
        final String url;
        final String file;
        final int size;
        final String hash;

        int bytesDownloaded = 0;

        // constructors -------------------------------------------------------

        PackageInfo(String url, String file, int size, String hash)
        {
            this.url = url;
            this.file = file;
            this.size = size;
            this.hash = hash;
        }

        // Object methods -----------------------------------------------------

        public String toString()
        {
            return "PackageInfo url: " + url + " file: " + file + " size: " + size + " hash: " + hash;
        }
    }
}
