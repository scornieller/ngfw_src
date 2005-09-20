/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mail.impl.imap;

import com.metavize.tran.mail.papi.imap.IMAPTokenizer;
import java.nio.ByteBuffer;
import static com.metavize.tran.util.Ascii.*;
import static com.metavize.tran.util.ASCIIUtil.*;
import org.apache.log4j.Logger;
import sun.misc.BASE64Decoder;

/**
 * Receives ByteBuffers to/from server.  A subtle point
 * is that this may not see mails (depending on if it
 * is on the client or server casing), and cannot
 * request more data (via "returning" a read buffer).  
 * The first point does not matter, as this class does
 * not care about mail data.  For the second point, we ensure
 * that the Parser takes care to only pass ByteBuffers
 * aligned on token boundaries.  Even if this is located
 * before the Parser's logic, the parser will cause bytes
 * to be pushed-back and re-seen.
 * <br><br>
 * An ImapSessionMonitor works by passing tokens and literals
 * to an internal collection of {@link com.metavize.tran.mail.impl.ima.TokMon TokMon}s.
 * The design of the TokMon API was to prevent having to duplicate and have
 * each "independent area of interest" re-tokenize the buffers.  Instead,
 * the ImapSessionMonitor performs tokenizing, and passes each token to
 * its consisuent TokMons.
 */
class ImapSessionMonitor {

  private final Logger m_logger =
    Logger.getLogger(ImapSessionMonitor.class);

  private String m_userName;
  private IMAPTokenizer m_fromServerTokenizer;
  private IMAPTokenizer m_fromClientTokenizer;
  private IntHolder m_literalFromServerCount;
  private IntHolder m_literalFromClientCount;

  private TokMon[] m_tokMons;

  private static final String LOGIN_SASL_MECH_NAME = "LOGIN";//Undocumented, I assume no encryption
  private static final String PLAIN_SASL_MECH_NAME = "PLAIN";//Cannot be encrypted (or, more to the
                                                             //point, should already be over an
                                                             //encrypted channel).  RFC 2595
  private static final String GSSAPI_SASL_MECH_NAME = "GSSAPI";//May be encrypted, RFC 2222
  private static final String ANONYMOUS_SASL_MECH_NAME = "ANONYMOUS";//Auth only, RFC 2245
  private static final String CRAM_MD5_SASL_MECH_NAME = "CRAM-MD5";//Auth only, RFC 2195
  private static final String DIGEST_MD5_SASL_MECH_NAME = "DIGEST-MD5";//May be encrypted, RFC 2831
  private static final String KERBEROS_V4_SASL_MECH_NAME = "KERBEROS_V4";//May be encrypted, RFC 2222
  private static final String SKEY_SASL_MECH_NAME = "SKEY";//No security, RFC 2222
  private static final String EXTERNAL_SASL_MECH_NAME = "EXTERNAL";//Assume can be encrypted, RFC 2222
  private static final String SECURID_SASL_MECH_NAME = "SECURID";//Auth only,  RFC 2808
  private static final String SRP_SASL_MECH_NAME = "SRP";//May be encrypted
  private static final String SCRAM_MD5_SASL_MECH_NAME = "SCRAM-MD5";//Don't know, assume encrypted
  private static final String NTLM_SASL_MECH_NAME = "NTLM";//Undocumented, assume encrypted


  ImapSessionMonitor() {
    m_fromServerTokenizer = new IMAPTokenizer();
    m_fromClientTokenizer = new IMAPTokenizer();
    m_literalFromServerCount = new IntHolder();
    m_literalFromClientCount = new IntHolder();
    m_tokMons = new TokMon[] {
      new AUTHENTICATETokMon(this),
      new LOGINTokMon(this),
      new STARTTLSTokMon(this)
    };
  }

  boolean hasUserName() {
    return m_userName != null;
  }

  /**
   * Get the UserName, as observed by one of the TokMons.
   * This may be null for the entire duration of the
   * session
   *
   * @return the username, or null.
   */
  String getUserName() {
    return m_userName;
  }

  /**
   * Call to set the UserName.  This is intended for use
   * by the various {@link com.metavize.tran.mail.impl.imap.SASLTransactionTokMon SASL monitors}
   * or the vanilla {@link com.metavize.tran.mail.impl.imap.LOGINTokMon LOGIN command TokMon}
   *
   * @param userName the userName
   */
  void setUserName(String userName) {
    m_userName = userName;
  }

  /**
   * The ByteBuffer is assumed to be a duplicate, so its position
   * can be messed-with but not its contents.
   * 
   * @return true if passthru should be entered (and this object should
   *         never be called again).
   *
   */
  boolean bytesFromClient(ByteBuffer buf) {
    return handleBytes(buf, true);
  }

  /**
   * @return true if passthru should be entered.
   */
  boolean bytesFromServer(ByteBuffer buf) {
    return handleBytes(buf, false);
  }

  /**
   * Replace one TokMon with another.  This is used for
   * the situation like SASL, where one monitor detects the start
   * of a SASL negotiation, and delegates to the specfic mechanism's
   * Monitor.  When complete, the reverse replacement can be made.
   */
  void replaceMonitor(TokMon old, TokMon replacement) {
    for(int i = 0; i<m_tokMons.length; i++) {
      if(m_tokMons[i] == old) {
        m_tokMons[i] = replacement;
        break;
      }
    }
  }

  SASLTransactionTokMon getSASLMonitor(TokMon currentMonitor,
    String mechanismName) {

    if(mechanismName == null) {
      m_logger.debug("Null SASL mechanism.  Return null");
      return null;
    }
    mechanismName = mechanismName.trim();

    if(mechanismName.equalsIgnoreCase(LOGIN_SASL_MECH_NAME)) {
      return new LOGINSaslTokMon(this, currentMonitor);
    }
    if(mechanismName.equalsIgnoreCase(PLAIN_SASL_MECH_NAME)) {
      return new PLAINSaslTokMon(this, currentMonitor);
    }
    if(
      mechanismName.equalsIgnoreCase(ANONYMOUS_SASL_MECH_NAME) ||
      mechanismName.equalsIgnoreCase(CRAM_MD5_SASL_MECH_NAME) ||
      mechanismName.equalsIgnoreCase(SKEY_SASL_MECH_NAME) ||
      mechanismName.equalsIgnoreCase(SECURID_SASL_MECH_NAME)) {
      m_logger.debug("SASL Mechanism \"" +
        mechanismName + "\" has no handler, but it cannot result " +
        "in encrypted channel.  Return passthru handler");
      return new PassthruSaslTokMon(this, currentMonitor);
    }

    m_logger.warn("Unable to provide SASL handler for mechanism \"" +
      mechanismName + "\".  Punt");
    //TODO bscott we *could* at least see if server rejects *then*
    //punt, but that it likely overkill
    return null;
  }

  private boolean handleBytes(final ByteBuffer buf,
    final boolean fromClient) {

    TokMon[] tokMons = m_tokMons;

    final IMAPTokenizer tokenizer = fromClient?
      m_fromClientTokenizer:m_fromServerTokenizer;
      
    final IntHolder intHolder = fromClient?
      m_literalFromClientCount:m_literalFromServerCount;

    while(buf.hasRemaining()) {
      if(intHolder.val > 0) {
        int toSkip = intHolder.val > buf.remaining()?
          buf.remaining():intHolder.val;
        for(TokMon tm : tokMons) {
          if(tm.handleLiteral(buf, toSkip, fromClient)) {
            return true;
          }
        }
        intHolder.val-=toSkip;
        buf.position(buf.position() + toSkip);
        continue;
      }

      IMAPTokenizer.IMAPNextResult result = tokenizer.next(buf);
      
      if(result == IMAPTokenizer.IMAPNextResult.NEED_MORE_DATA) {
        m_logger.debug("Need more data");
        return true;
      }
      if(result == IMAPTokenizer.IMAPNextResult.EXCEEDED_LONGEST_WORD) {
        m_logger.warn("Exceeded longest WORD.  Assume some encryption and enter passthru");
        return true;
      }

      for(TokMon tm : tokMons) {
        if(tm.handleToken(tokenizer, buf, fromClient)) {
          return true;
        }
      }
      if(tokenizer.isTokenLiteral()) {
        intHolder.val = tokenizer.getLiteralOctetCount();
      }
      
    }
    return false;
  }

  private final class IntHolder {
    int val = 0;
  }
}   






class STARTTLSTokMon
  extends TokMon {

  private static final byte[] STARTTLS_BYTES = "starttls".getBytes();

  private final Logger m_logger =
    Logger.getLogger(STARTTLSTokMon.class);

  STARTTLSTokMon(ImapSessionMonitor sesMon) {
    super(sesMon);
    m_logger.debug("Created");
  }

  protected boolean handleTokenFromClient(IMAPTokenizer tokenizer,
    ByteBuffer buf) {

    if(
      getClientRequestTokenCount() == 2 &&
      !tokenizer.isTokenEOL() &&
      getClientReqType() == ClientReqType.TAGGED &&
      tokenizer.compareWordAgainst(buf, STARTTLS_BYTES, true)
      ) {
      m_logger.debug("STARTTLS command issued from client.  Assume" +
        "this will succeed and thus go into passthru mode");
      return true;
    }
    return false;
  }
}


    
class LOGINTokMon
  extends TokMon {

  private static final byte[] LOGIN_BYTES = "login".getBytes();
  private static final int MAX_REASONABLE_UID_AS_LITERAL = 1024*2;  

  private enum LTMState {
    NONE,
    TAGGED_SUSPECT,
    LOGIN_FOUND
  };

  private final Logger m_logger =
    Logger.getLogger(LOGINTokMon.class);

  private LTMState m_state = LTMState.NONE;
  private byte[] m_literalUID;
  private int m_nextLiteralPos;

  LOGINTokMon(ImapSessionMonitor sesMon) {
    super(sesMon);
    m_logger.debug("Created");
  }



  protected boolean handleLiteralFromClient(ByteBuffer buf, int bytesFromPosAsLiteral) {
    if(m_literalUID == null) {
      return false;
    }
    if((m_literalUID.length - m_nextLiteralPos) < bytesFromPosAsLiteral) {
      m_logger.error("Expecting to collect a literal of length " +
        m_literalUID.length + " as username, yet received too many" +
        "bytes.  Tracking error");
      m_literalUID = null;
      return false;
    }
    for(int i = 0; i<bytesFromPosAsLiteral; i++) {
      m_literalUID[m_nextLiteralPos++] = buf.get(buf.position() + i);
    }
    if(m_nextLiteralPos >= m_literalUID.length) {
      m_literalUID = null;
      m_nextLiteralPos = 0;
      setSessionUserName(new String(m_literalUID));
    }
    return false;
  }

  private void setSessionUserName(String userName) {
    if(userName == null) {
      getSessionMonitor().setUserName(userName);
    }
    else {
      m_logger.debug("Found username \"" + userName + "\" in LOGIN authentication");
    }
  }

  protected boolean handleTokenFromClient(IMAPTokenizer tokenizer,
    ByteBuffer buf) {

    //Quick bypass for impossible lines
    if(getClientRequestTokenCount() > 3) {
      m_state = LTMState.NONE;
      return false;
    }

    switch(m_state) {
      case NONE:
        if(
          getClientRequestTokenCount() == 1 &&
          !tokenizer.isTokenEOL() &&
          getClientReqType() == ClientReqType.TAGGED
          ) {
          m_state = LTMState.TAGGED_SUSPECT;
        }
        break;
      case TAGGED_SUSPECT:
        if(getClientRequestTokenCount() == 2 &&
          !tokenizer.isTokenEOL() &&
          tokenizer.compareWordAgainst(buf, LOGIN_BYTES, true)
          ) {
          m_state = LTMState.LOGIN_FOUND;
        }
        else {
          m_state = LTMState.NONE;
        }
        break;
      case LOGIN_FOUND:
        //TODO bscott Remove this from the list of Monitors.  The odds
        //of someone re-authenticating or having the login fail and another
        //come in is really low
        switch(tokenizer.getTokenType()) {
          case WORD:
            setSessionUserName(tokenizer.getWordAsString(buf));
            break;
          case QSTRING:
            setSessionUserName(new String(tokenizer.getQStringToken(buf)));
            break;
          case LITERAL:
            m_logger.debug("username is a LITERAL (collect on subsequent calls)");
            if(tokenizer.getLiteralOctetCount() > MAX_REASONABLE_UID_AS_LITERAL) {
              m_logger.error("Received a LOGIN uid as a literal or length: " +
                tokenizer.getLiteralOctetCount() + ".  This exceeds the reasonable" +
                " limit of " + MAX_REASONABLE_UID_AS_LITERAL + ".  This is either a " +
                "state-tracking bug, or someone really clever trying to cause some " +
                "DOS-style attack on this process");
            }
            else {
              m_literalUID = new byte[tokenizer.getLiteralOctetCount()];
              m_nextLiteralPos = 0;
            }
            break;
          case CONTROL_CHAR:
            String ctlUserName = new String(new byte[] {buf.get(tokenizer.getTokenStart())});
            m_logger.warn("Username is also a control character \"" + ctlUserName + "\" (?!?)");
            setSessionUserName(ctlUserName);
            break;
          case NEW_LINE:
            m_logger.debug("Expecting username token, got EOL.  Assume server will return error");
          case NONE:
        }
        m_state = LTMState.NONE;
        break;//Redundant
    }
    return false;
  }

}
 

/**
 * Looks for the AUTHENTICATE command,
 * then replaces itself with an appropriate
 * SASL TokMon
 */
class AUTHENTICATETokMon
  extends CommandTokMon {

  private static final byte[] AUTHENTICATE_BYTES =
    "authenticate".getBytes();

  private final Logger m_logger =
    Logger.getLogger(AUTHENTICATETokMon.class);

  private StringBuilder m_mechNameSB;

  AUTHENTICATETokMon(ImapSessionMonitor sesMon) {
    super(sesMon);
    m_logger.debug("Created");
  }
  AUTHENTICATETokMon(ImapSessionMonitor sesMon,
    TokMon tokMon) {
    super(sesMon, tokMon);
    m_logger.debug("Created");
  }

  @Override
  protected final boolean testCommand(IMAPTokenizer tokenizer,
    ByteBuffer buf) {
    return tokenizer.compareWordAgainst(buf, AUTHENTICATE_BYTES, true);
  }


  @Override
  protected boolean handleTokenFromServer(IMAPTokenizer tokenizer,
    ByteBuffer buf) {
    //We never care about this
    return false;
  }

  protected boolean handleTokenFromClient(IMAPTokenizer tokenizer,
    ByteBuffer buf) {
    //Instances of this class will only care about the "OPEN"
    //state, as "NONE" means we're not in an AUTHENTICATE
    //command, and "CLOSING" means we've been swapped-back
    //
    if(getCommandState() == CommandState.OPEN) {
      //Make sure not to grab the "AUTHENTICATE"
      //word itself
      if(getClientRequestTokenCount() <= 2) {
        return false;
      }
      //Things are tricky/nasty, as I'm not sure
      //if our IMAP tokens can apear in a mechanism
      //name.  As-such, we do our old "trick" to
      //accumulate a ByteByffer
      if(tokenizer.isTokenEOL()) {
        if(m_mechNameSB == null || m_mechNameSB.length() == 0) {
          m_logger.warn("Unable to determine AUTHENTICATE mechanism.  Assume " +
            "worst case that this channel will become encrypted and " +
            "punt");
          return true;
        }
        String mechName = m_mechNameSB.toString();
        m_logger.debug("Mechanism name: \"" + mechName + "\"");

        SASLTransactionTokMon newMon = getSessionMonitor().getSASLMonitor(
          this,
          mechName);

        if(newMon != null) {
          getSessionMonitor().replaceMonitor(this, newMon);
          return false;
        }
        else {
          m_logger.warn("Unknown SASL mechanism \"" +
            mechName + "\".  Give up on this session (passthru)");
          return true;
        }
      }
      else {
        if(m_mechNameSB == null) {
          m_mechNameSB = new StringBuilder();
        }
        m_mechNameSB.append(tokenizer.tokenToStringDebug(buf));
      }
    }
    return false;
  }
}


class PLAINSaslTokMon
  extends SASLTransactionTokMon {

  private final Logger m_logger =
    Logger.getLogger(PLAINSaslTokMon.class);

  

  PLAINSaslTokMon(ImapSessionMonitor sesMon,
    TokMon state) {
    super(sesMon, state);
    m_logger.debug("Created");
  }


  protected boolean clientMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {
    return false;
  }
    
  protected boolean serverMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {
    return false;
  }


  
}



/**
  * After extensive investigations, I've determined that
  * there is no standard for this type of authentication
  *
  * Begins life just after the "plain" mechanism
  * has been declared.
  *
  * General protocol *seems* to be as follows:
  *
  * s: User Name null
  * c: + my_user_name
  * s: Password null
  * c: + my_password
  *
  * Where all data is base64 encoded (except the "+").  There seems
  * to be a null byte (0) at the end of each server challenge, although
  * I'm going to make it optional.
  *
  * We thus wait for the first EOL from the server, and begin
  * examining.  We end when a line from the server is
  * not a continuation request.
  *
  * The anoying thing is that "+" is part of the Base64 alphabet,
  * as well as the IMAP token set.  Rather than changing the delimiters/tokens
  * for the Tokenizer, I'll just concatenate concurrent tokens until an EOL.
  *
  * This will break if Client pipelines (sends UID/PWD before the server
  * prompts).  The alternative is to simply use the first complete
  * line from the client, but we risk (if things were out-or-order) printing
  * folks passwords into reports.
  *
  */
class LOGINSaslTokMon
  extends SASLTransactionTokMon {

  private final Logger m_logger =
    Logger.getLogger(LOGINSaslTokMon.class);


  private static final byte[] USERNAME_CHALLENGE_BYTES =
    "User Name".getBytes();

  private boolean m_serverLastWasUserName = false;       
  
  LOGINSaslTokMon(ImapSessionMonitor sesMon,
    TokMon state) {
    super(sesMon, state);
    m_logger.debug("Created");
  }

  protected boolean clientMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {

    if(m_serverLastWasUserName) {
      try {
        String uid = new String(message);
        m_logger.debug("Found AUTHENTICATE PLAIN username to be \"" +
          uid + "\"");
        getSessionMonitor().setUserName(uid);
      }
      catch(Exception ex) {
        m_logger.warn("Exception converting AUTHENTICATION PLAIN username to a String", ex);
      }
    }
    return false;
  }
    
  protected boolean serverMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {

    boolean nullTerminated = message[message.length-1] == (byte) 0;
    
    m_serverLastWasUserName = compareArrays(
      USERNAME_CHALLENGE_BYTES,
      0,
      USERNAME_CHALLENGE_BYTES.length,
      message,
      0,
      nullTerminated?message.length-1:message.length,
      true);

    return false;
  }    


}

class PassthruSaslTokMon
  extends SASLTransactionTokMon {

  private final Logger m_logger =
    Logger.getLogger(PassthruSaslTokMon.class);  
  
  PassthruSaslTokMon(ImapSessionMonitor sesMon,
    TokMon state) {
    super(sesMon, state);
    m_logger.debug("Created");
  }

  protected boolean clientMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {
    return false;
  }
    
  protected boolean serverMessage(IMAPTokenizer tokenizer,
    ByteBuffer buf,
    byte[] message) {
    return false;
  }

  
  
}








