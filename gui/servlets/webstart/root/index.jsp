<%@ page language="java" import="com.untangle.uvm.*" %>
<%
UvmLocalContext uvm = UvmContextFactory.context();
boolean reportingEnabled = uvm.reportingManager().isReportingEnabled();
String host=request.getHeader("host");
String scheme=request.getScheme();
String ctxPath=request.getContextPath();
String cb = scheme + "://" + host + ctxPath + "/";
String pageName = request.getServletPath();

boolean isIndex = pageName.equals("/index.html");
boolean isDownload = pageName.equals("/download.html");
boolean isSecure   = scheme.equals("https");

/* If they request anything else, give them the index page */
if (!( isIndex || isDownload)) isIndex = true;
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>Untangle Client Launcher</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<style type="text/css">
/* <![CDATA[ */
@import url(/images/base.css);
/* ]]> */
</style>
<% if ( isIndex ) { %>
<script type="text/javascript">
// <![CDATA[
var javawsInstalled    = 0;
var javaws142Installed = 0;
var javaws150Installed = 0;
isIE = "false";
if (navigator.mimeTypes && navigator.mimeTypes.length) {
    x = navigator.mimeTypes['application/x-java-jnlp-file'];
    if (x) {
        javawsInstalled = 1;
        javaws142Installed=1;
        javaws150Installed=1;
    }
}
else {
  isIE = "true";
}

function showMessage() {
  if ( javaws150Installed == 0 && ( navigator.userAgent.indexOf("Gecko") == -1 )) {
     document.write( 'Java&trade; v1.5 was not detected.  You may need to download and install Java&trade; v1.5.<br /><br />' );
  }
}
// ]]>
</script>
<!--[if lt ie 9]>
<script type="text/vbscript">
    on error resume next
    If isIE = "true" Then
            If Not(IsObject(CreateObject("JavaWebStart.isInstalled"))) Then
                    javawsInstalled = 0
            Else javawsInstalled = 1
            End If
            If Not(IsObject(CreateObject("JavaWebStart.isInstalled.1.4.2.0"))) Then
                    javaws142Installed = 0
            Else javaws142Installed = 1
            End If
            If Not(IsObject(CreateObject("JavaWebStart.isInstalled.1.5.0.0"))) Then
                    javaws150Installed = 0
            Else javaws150Installed = 1
            End If
    End If
</script>
<![endif]-->
<% } // if ( isIndex ) %>
</head>
<body>
<div id="main" style="width: 500px; margin: 50px auto 0 auto;">
 <!-- Box Start -->
 <div class="main-top-left"></div><div class="main-top-right"></div><div class="main-mid-left"><div class="main-mid-right"><div class="main-mid">
 <!-- Content Start -->
	
      <center>
            <img alt="Untangle" src="/images/Logo150x96.gif" />

            <div style="margin: 0 auto; width: 250px; padding: 40px 0 5px;">
<% if ( host.equalsIgnoreCase( "untangledemo.untangle.com" )  && !isDownload ) { %>

                <b>Login: untangledemo</b><br />
                <b>Password: untangledemo</b><br />

<% } %>
            
<% if ( isDownload ) { %>
            <object codebase="http://java.sun.com/update/1.5.0/jinstall-1_5_0_07-windows-i586.cab"
                            classid="clsid:5852F5ED-8BF4-11D4-A245-0080C6F74284"
                            height="0"
                            width="0">
              <param name="type" value="application/x-java-applet" />
              <param name="app" value="<%=cb%>gui.jnlp" />
              <param name="back" value="true" />
            </object>

            </div>            
            <!-- Alternate HTML for browsers which cannot instantiate the object -->
            <a href="http://www.untangle.com/javainstaller.html"><b>Download Java&trade; v1.5</b></a>
            
       </center>                                
<% } %>

<% if ( !isDownload ) { %>

                    <b>Server:</b> <em>&nbsp;<%=host %></em><br />
                    <b>Connection:</b> <em>
                         <% if(isSecure){  %>
                           &nbsp;https (secure)
                         <% } else { %>
                           &nbsp;http (standard)
                         <% } %>
                           </em><br />
              </div>
      </center>
      <br />
<% } // if ( !isDownload ) %>

<% if ( isIndex ) { %>
      <script type="text/javascript">
       // <![CDATA[
             showMessage();
      // ]]>
      </script>
    <% if ( !isDownload ) { %>
            <center>
              <a href="gui.jnlp"><b>Launch Untangle Client</b></a>
              <% if (reportingEnabled) { %>
                    <br /><a href="<%=scheme%>://<%=host%>/reports"><b>View Untangle Reports</b></a><br />
              <% } %>
            </center>
    <% } %>
<% } %>


      <div style="text-align: right; font-style: italic; margin-top:30px;">
       <a href="/java/jre-1_5_0_07-windows-i586-p.exe">Download Java&trade; v1.5 (Offline)</a><br />
<% if ( !isDownload ) { %>
             <a href="download.html">Download Java&trade; v1.5 (Online)</a><br />
<% } %>
      </div>

 <!-- Content End -->
 </div></div></div><div class="main-bot-left"></div><div class="main-bot-right"></div>
 <!-- Box End -->
</div>	
                       
</body>
</html>
