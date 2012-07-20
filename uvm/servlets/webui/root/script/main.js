Ext.namespace('Ung');
// Global Variables
// the main object instance
var main=null;
// Main object class
Ext.define("Ung.Main", {
    debugMode: false,
    buildStamp: null,
    disableThreads: false, // in development environment is useful to disable
                            // threads.
    leftTabs: null,
    appsSemaphore: null,
    apps: null,
    appsLastState: null,
    nodePreviews: null,
    config: null,
    nodes: null,
    // the Ext.Viewport object for the application
    viewport: null,
    initSemaphore: null,
    policySemaphore: null,
    contentLeftWidth: null,
    // the application build version
    version: null,
    iframeWin: null,
    IEWin: null,
    upgradeStatus:null,
    upgradeLastCheckTime: null,
    firstTimeRun: null,
    companyName: document.title,
    hostName: null,
    policyNodeWidget:null,
    
    // init function
    constructor: function(config) {
        Ext.apply(this, config);
    },

    init: function() {
        if (Ext.isGecko) {
            document.onkeypress = function(e) {
                if (e.keyCode==27) {
                    return false;
                }
                return true;
            };
        }
        this.firstTimeRun = Ung.Util.getQueryStringParam("firstTimeRun");
        this.appsLastState = {};
        this.nodePreviews = {};
        JSONRpcClient.toplevel_ex_handler = Ung.Util.rpcExHandler;
        JSONRpcClient.max_req_active = 2;

        this.initSemaphore = 1;
        rpc = {};
        // get JSONRpcClient
        rpc.jsonrpc = new JSONRpcClient("/webui/JSON-RPC");

        // below we load all the managers
        // this used to be done (and should be done asynchronously with each result calling postinit when compelet)
        // however this seems to cause some issues with the new firebug, which is now the only firebug.
        // in the meantime I've changed them all to load synchronously.
        // If we ever figure out the firebug issue we should revert back to the asynchronous loading of all the managers
        
        // get the language manager
        rpc.languageManager = rpc.jsonrpc.UvmContext.languageManager();
        i18n=new Ung.I18N({"map":rpc.languageManager.getTranslations("untangle-libuvm").map});
        Ext.MessageBox.wait(i18n._("Initializing..."), i18n._("Please wait"));
        Ext.defer(this.initSync, 5, this);
    },
    initSync: function() {
        rpc.languageSettings = rpc.languageManager.getLanguageSettings();

        // get the skin manager
        rpc.skinManager = rpc.jsonrpc.UvmContext.skinManager();
        // load the current skin
        var skinSettings = rpc.skinManager.getSettings();
        //TODO: find gray theme for extjs4
        //Ung.Util.loadCss("/skins/"+skinSettings.skinName+"/css/ext-skin.css");
        Ung.Util.loadCss("/skins/"+skinSettings.skinName+"/css/admin.css");
        if (skinSettings.outOfDate) {
            var win = new Ext.Window({
                layout: 'fit',
                width: 300,
                height: 200,
                closeAction: 'hide',
                plain: true,
                html: i18n._('The current custom skin is no longer compatible and has been disabled. The Default skin is temporarily being used. To disable this message change the skin settings under Config Administration. To get more information on how to fix the custom skin: <a href="http://wiki.untangle.com/index.php/Skins" target="_blank">Where can I find updated skins and new skins?</a>'),
                title: i18n._('Skin Out of Date'),
                buttons: [ {
                    text: i18n._('Ok'),
                    handler: function() {
                        win.hide();
                    }
                }]
            });
            win.show();
        }

        // get node manager
        rpc.nodeManager=rpc.jsonrpc.UvmContext.nodeManager();

        // get policy manager
        rpc.policyManager=rpc.jsonrpc.UvmContext.nodeManager().node("untangle-node-policy");

        // get toolbox manager
        rpc.toolboxManager=rpc.jsonrpc.UvmContext.toolboxManager();

        // get toolbox manager
        rpc.alertManager=rpc.jsonrpc.UvmContext.alertManager();
        
        // get admin manager
        rpc.adminManager=rpc.jsonrpc.UvmContext.adminManager();

        // get version
        rpc.version=rpc.jsonrpc.UvmContext.version();

        // get system manager
        rpc.systemManager=rpc.jsonrpc.UvmContext.systemManager();

        // get network manager
        rpc.networkManager=rpc.jsonrpc.UvmContext.networkManager();
        this.hostName = rpc.networkManager.getNetworkConfiguration().hostname; 
        this.setDocumentTitle();

        // get message manager & message key
        rpc.messageManager=rpc.jsonrpc.UvmContext.messageManager();
        rpc.messageKey = rpc.messageManager.getMessageKey();

        // get branding manager
        rpc.brandingManager = rpc.jsonrpc.UvmContext.brandingManager();
        this.companyName = rpc.brandingManager.getCompanyName();
        this.setDocumentTitle();

        this.startApplication();
    },
    initAsync: function() {
        //TODO: re-implement the init asynchronously when we switch to chrome browser
    },
    postinit: function() {
        this.initSemaphore--;
        if(this.initSemaphore!=0) {
            return;
        }
      this.startApplication();
    },
    setDocumentTitle: function() {
        document.title = main.companyName + ((main.hostName!=null)?(" - " + main.hostName):"");
    },
    warnOnUpgrades: function(handler) {
        if(main.upgradeStatus!=null && main.upgradeStatus.upgradesAvailable ) {
            main.warnOnUpgradesCallback(main.upgradeStatus,handler);
        } else {
            if(main.upgradeLastCheckTime!=null && (new Date()).getTime()-main.upgradeLastCheckTime<300000 && main.upgradeStatus!=null) {
                main.warnOnUpgradesCallback(main.upgradeStatus,handler);
            } else {
                Ext.MessageBox.wait(i18n._("Checking for available upgrades..."), i18n._("Please wait"));
                rpc.toolboxManager.getUpgradeStatus(Ext.bind(function(result, exception,opt,handler) {
                    main.upgradeLastCheckTime=(new Date()).getTime();
                    Ext.MessageBox.hide();
                    if(Ung.Util.handleException(exception, Ext.bind(function() {
                        main.warnOnUpgradesCallback(main.upgradeStatus,handler);
                    }, this))) return;

                    main.upgradeStatus=result;
                    main.warnOnUpgradesCallback(main.upgradeStatus,handler);
                }, this,[handler],true),true);
            }
        }
    },
    warnOnUpgradesCallback: function (upgradeStatus,handler) {
        if(upgradeStatus!=null) {
            if(upgradeStatus.upgrading) {
                Ext.MessageBox.alert(i18n._("Failed"), "Upgrade in progress.");
                return;
            } else if(upgradeStatus.upgradesAvailable) {
                Ext.getCmp("configItem_upgrade").setIconCls("icon-config-upgrade-available");
                Ext.Msg.show({
                    title:i18n._("Upgrades warning"),
                    msg: i18n._("Upgrades are available. You must perform all possible upgrades before downloading from the library. Please click OK to open Upgrade panel."),
                    buttons: Ext.Msg.OKCANCEL,
                    fn: function (btn, text) {
                        if (btn == 'ok') {
                            main.leftTabs.setActiveTab('leftTabConfig');
                            Ext.getCmp("configItem_upgrade").onClick();
                        }
                    },
                    icon: Ext.MessageBox.QUESTION
                });
                return;
            }
        }
        handler.call(this);
    },
    resetAppLastState: function(displayName) {
      main.appsLastState[displayName]=null;
    },
    setAppLastState: function(displayName,state,options,download) {
        if(state==null) {
            main.appsLastState[displayName]=null;
        } else {
            main.appsLastState[displayName]={state:state, options:options, download:download};
        }
    },
    startApplication: function() {
        Ext.MessageBox.wait(i18n._("Starting..."), i18n._("Please wait"));
        this.initExtI18n();
        this.initExtGlobal();
        this.initExtVTypes();
        Ext.EventManager.onWindowResize(Ung.Util.resizeWindows);
        // initialize viewport object
        var contentRightArr=[
            '<div id="content-right">',
                '<div id="racks" style="">',
                    '<div id="rack-list"><div id="rack-select-container"></div><div id="parent-rack-container"></div><div id="alert-container" style="display:none;"></div><div id="no-ie-container" style="display:none;"></div>',
                    '</div>',
                    '<div id="rack-nodes">',
                        '<div id="filter_nodes"></div>',
                        '<div id="nodes-separator" style="display:none;"><div id="nodes-separator-text"></div></div>',
                        '<div id="service_nodes"></div>',
                    '</div>',
                '</div>',
            '</div>'];

        var cssRule = Ext.util.CSS.getRule(".content-left",true);
        this.contentLeftWidth = ( cssRule ) ? parseInt( cssRule.style.width ): 214;
        this.viewport = Ext.create('Ext.container.Viewport',{
            layout:'border',
            items:[{
                region: 'west',
                id: 'west',
                //split: true,
                buttonAlign: 'center',
                cls: "content-left",
                border: false,
                width: this.contentLeftWidth,
                bodyStyle: 'background-color: transparent;',
                footer: false,
                buttonAlign: 'left',
                items: [{
                    cls: "logo",
                    html: '<img src="/images/BrandingLogo.gif?'+(new Date()).getTime()+'" border="0"/>',
                    border: false,
                    height: 100,
                    bodyStyle: 'background-color: transparent;'
                }, {
                    layout: "anchor",
                    border: false,
                    cls: "left-tabs",
                    items: this.leftTabs = new Ext.TabPanel({
                        activeTab: 0,
                        height: 400,
                        anchor: "100% 100%",
                        autoWidth: true,
                        layoutOnTabChange: true,
                        deferredRender: false,
                        defaults: {
                            anchor: '100% 100%',
                            autoWidth: true,
                            autoScroll: true
                        },
                        items:[{
                            title: i18n._('Apps'),
                            id: 'leftTabApps',
                            helpSource: 'apps',
                            html:'<div id="appsItems"></div>',name:'Apps'
                        },{
                            title: i18n._('Config'),
                            id: 'leftTabConfig',
                            html: '<div id="configItems"></div>',
                            helpSource: 'config',
                            name: 'Config'
                        }],
                        listeners: {
                            "render": {
                                fn: function() {
                                    this.addNamesToPanels();
                                }
                            }
                        }
                    })
                }],
                bbar: Ext.create('Ext.toolbar.Toolbar',{columns:3,style:'text-align:left',items:[{
                    xtype: 'button',
                    name: 'Help',
                    iconCls: 'icon-help',
                    text: i18n._('Help'),
                    handler: function() {
                        var helpSource=main.leftTabs.getActiveTab().helpSource;
                        main.openHelp(helpSource);
                    }
                }, {
                    name: 'MyAccount',                       
                    iconCls: 'icon-myaccount',
                    text: i18n._('My Account'),
                    tooltip: i18n._('You can access your online account and reinstall apps you already purchased, redeem vouchers, or buy new ones.'),
                    handler: function() {
                       main.openStore("my_account", i18n._("My Account"));
                    }
                }, {
                    xtype: 'button',
                    name: 'Logout',
                    iconCls: 'icon-logout',
                    text: i18n._('Logout'),
                    handler: function() {
                        window.location.href = '/auth/logout?url=/webui&realm=Administrator';
                    }
                }]})
             }, {
                region:'center',
                id: 'center',
                html: contentRightArr.join(""),
                border: false,
                cls: 'center-region',
                bodyStyle: 'background-color: transparent;',
                autoScroll: true
            }
        ]});
        Ext.QuickTips.init();

        main.systemStats = new Ung.SystemStats({});
        Ext.getCmp("west").on("resize", function() {
            var newHeight = Math.max(this.getEl().getHeight()-175,100);
            main.leftTabs.setHeight(newHeight);
        });

        Ext.getCmp("west").fireEvent("resize");
        this.loadConfig();
        this.loadPolicies();
    },
    openStore: function (action,title) {
        var currentLocation = window.location;
        var query = "host=" + currentLocation.hostname;
        query += "&port=" + currentLocation.port;
        query += "&protocol=" + currentLocation.protocol.replace(/:$/, "");
        query += "&action="+action;

        this.openWindow( query, storeWindowName, title );
    },
    openStoreToLibItem: function (libItemName, title,action) {
        var currentLocation = window.location,
        query = "host=" + currentLocation.hostname;
        if(!action) {
            action = 'browse';
        } else {
            if(action != 'buy') {
                action = "browse";
            }
        }
        query += "&port=" + currentLocation.port;
        query += "&protocol=" + currentLocation.protocol.replace(/:$/, "");
        query += "&action="+action;
        query += "&libitem=" + libItemName;

        this.openWindow( query, storeWindowName, title );
    },
    openWindow: function( query, windowName, title, url) {
        if( url==null ) {
            url =   '../library/launcher?' + query;
        }
        /* browser specific code ... we has it. */
        if ( !Ext.isIE ) {
            this.openIFrame(url, title);
            return;
        }

        /** This code is not used for now we just open in an iframe as above */
        /* If we decide to go back to a new window for whatever then use this */
        var w = window.open(url, windowName, "location=0, resizable=1, scrollbars=1");

        var m = Ext.String.format( i18n._( "Click {1}here{2} or disable your pop-up blocker and try again." ),
                               '<br/>', "<a href='" + url + "' target='" + windowName + "'>", '</a>' );

        if ( w == null ) {
            Ext.MessageBox.show({
                title: i18n._( "Unable to open a new window" ),
                msg: m,
                buttons: Ext.MessageBox.OK,
                icon: Ext.MessageBox.INFO
            });
        } else {
            if ( w ) w.focus();
            this.IEWin = w;
        }
    },
    openIFrame: function( url, title ) {
        var iframeWin = main.getIframeWin();
        iframeWin.show();
        iframeWin.setTitle(title);
        window.frames["iframeWin_iframe"].location.href = url;
    },
    setIFrameLocation: function (url) {
        window.frames["iframeWin_iframe"].location.href = url;
    },

    initExtI18n: function() {
        var locale = rpc.languageSettings.language;
        if(locale) {
          Ung.Util.loadScript('/ext4/locale/ext-lang-' + locale + '.js', main.overrideLanguageSettings);
        } else {
            main.overrideLanguageSettings();
        }
    },
    overrideLanguageSettings: function () {
        // Uncomment this to override the language timefield format for the current language
        //TODO: consider adding support to set this in a Time Format section in Config -> Settings -> Regional Settings (would be stored in AdminManager.AdminSettings)
        /*
        Ext.apply(Ext.form.field.Time.prototype, {
            format : "H:i"    //may also use: "g:i A"
        });
        */
    },
    initExtGlobal: function() {
        // init quick tips
        Ext.QuickTips.init();
    },
    // Add the additional 'advanced' VTypes
    initExtVTypes: function() {
        Ext.apply(Ext.form.VTypes, {
          ipAddress: function(val) {
              if ( val.indexOf("/") == -1 && val.indexOf(",") == -1 && val.indexOf("-") == -1) {
                  switch(val) {
                      case 'any':
                          return true;
                      default:
                          return Ung.RuleValidator.isSingleIpValid(val);
                  }
              }
              if ( val.indexOf(",") != -1) {
                  return Ung.RuleValidator.isIpListValid(val);
              } else {
                  if ( val.indexOf("-") != -1) {
                      return Ung.RuleValidator.isIpRangeValid(val);
                  }
                  if ( val.indexOf("/") != -1) {
                      var cidrValid = Ung.RuleValidator.isCIDRValid(val);
                      var ipNetmaskValid = Ung.RuleValidator.isIpNetmaskValid(val);
                      return cidrValid || ipNetmaskValid;
                  }
                  console.log("Unhandled case while handling vtype for ipAddr:", val, " returning true !");
                  return true;
              }
          },
          ipAddressText: i18n._('Invalid IP Address.'),

          ipAddressMatcher: function(val) {
            var ipAddrMaskRe = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
            return ipAddrMaskRe.test(val);
          },
          ipAddressMatcherText: i18n._('Invalid IP Address.'),

          port: function(val) {
            switch(val) {
                case 'any':
                    return true;
                default:
                    if ( val.indexOf('-') == -1 && val.indexOf(',') == -1) {
                        return Ung.RuleValidator.isSinglePortValid(val);
                    }
                    if ( val.indexOf('-') != -1 && val.indexOf(',') == -1) {
                        return Ung.RuleValidator.isPortRangeValid(val);
                    }
                    return Ung.RuleValidator.isPortListValid(val);
            }
          },
          portText: Ext.String.format(i18n._("The port must be an integer number between {0} and {1}."), 1, 65535),

          portMatcher: function(val) {
            var minValue = 1;
            var maxValue = 65535;
            return (minValue <= val && val <= maxValue) || (val == 'any' || val == 'all' || val == 'n/a' || val == 'none');
          },
          portMatcherText: Ext.String.format(i18n._("The port must be an integer number between {0} and {1} or one of the following values: any, all, n/a, none."), 1, 65535),

          password: function(val) {
            if (field.initialPassField) {
              var pwd = Ext.getCmp(field.initialPassField);
              return (val == pwd.getValue());
            }
            return true;
          },
          passwordText: i18n._('Passwords do not match'),
        });
    },
    upgrade: function () {
        Ext.MessageBox.wait(i18n._("Downloading updates..."), i18n._("Please wait"));
        Ung.MessageManager.startUpgradeMode();
        rpc.toolboxManager.upgrade(Ext.bind(function(result, exception) {
            if(Ung.Util.handleException(exception)) return;
        }, this));
    },

    getNetworkManager: function(forceReload) {
        if (forceReload || rpc.networkManager === undefined) {
            try {
                rpc.networkManager = rpc.jsonrpc.UvmContext.networkManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return rpc.networkManager;
    },

    getLoggingManager: function(forceReload) {
        if (forceReload || rpc.loggingManager === undefined) {
            try {
                rpc.loggingManager = rpc.jsonrpc.UvmContext.loggingManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return rpc.loggingManager;
    },

    getAppServerManager: function(forceReload) {
        if (forceReload || rpc.appServerManager === undefined) {
            try {
                rpc.appServerManager = rpc.jsonrpc.UvmContext.appServerManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return rpc.appServerManager;
    },

    getBrandingManager: function(forceReload) {
        if (forceReload || rpc.brandingManager === undefined) {
            try {
                rpc.brandingManager = rpc.jsonrpc.UvmContext.brandingManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return rpc.brandingManager;
    },

    getOemManager: function(forceReload) {
        if (forceReload || rpc.oemManager === undefined) {
            try {
                rpc.oemManager = rpc.jsonrpc.UvmContext.oemManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }

        }
        return rpc.oemManager;
    },
    
    getLicenseManager: function(forceReload) {
        // default functionality is to reload license manager as it might change in uvm
        if (typeof forceReload === 'undefined') {
            forceReload = true;
        }
        if (forceReload || rpc.licenseManager === undefined) {
            try {
              rpc.licenseManager = rpc.jsonrpc.UvmContext.licenseManager();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return rpc.licenseManager;
    },

    getLocalDirectory: function(forceReload) {
        if (forceReload || rpc.localDirectory === undefined) {
            try {
                rpc.localDirectory = rpc.jsonrpc.UvmContext.localDirectory();
            } catch (e) {
                Ung.Util.rpcExHandler(e);
            }
        }
        return rpc.localDirectory;
    },

    getMailSender: function(forceReload) {
        if (forceReload || rpc.mailSender === undefined) {
            try {
                rpc.mailSender = rpc.jsonrpc.UvmContext.mailSender();
            } catch (e) {
            Ung.Util.rpcExHandler(e);
            }
        }
        return rpc.mailSender;
    },

    unactivateNode: function(packageDesc) {
        Ung.AppItem.updateState(packageDesc.displayName,"unactivating");
        rpc.nodeManager.nodeInstances(Ext.bind(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                var tids=result;
                if(tids.length>0) {
                    Ung.AppItem.updateState(this.displayName);
                    Ext.MessageBox.alert(this.name+" "+i18n._("Warning"),
                    Ext.String.format(i18n._("{0} cannot be removed because it is being used by the following rack:{1}You must remove the product from all racks first."), this.displayName,"<br><b>"+tids[0].policy.name+"</b><br><br>"));
                    return;
                } else {
                    rpc.toolboxManager.uninstall(Ext.bind(function (result, exception) {
                       if(Ung.Util.handleException(exception)) return;
                       main.setAppLastState(this.displayName);
                       main.loadApps();
                        /*
                        rpc.toolboxManager.unregister(Ext.bind(function (result, exception) {
                            if(Ung.Util.handleException(exception)) return;
                            main.loadApps();
                        }, this), this.name);
                        */
                    }, this), this.name);
                }
        },packageDesc), packageDesc.name);
    },

    // open context sensitive help
    openHelp: function(source) {
        var url = "../library/launcher?";
        url += "action=help";
        if(source) {
            url += "&source=" + source;
        }
        window.open(url);
    },
    
    // load policies list
    loadPolicies: function() {
        Ext.MessageBox.wait(i18n._("Loading Apps..."), i18n._("Please wait"));
        if (rpc.policyManager != null) {
            rpc.policyManager.getSettings(Ext.bind(function (result, exception) {
                if(Ung.Util.handleException(exception)) return;
                rpc.policies=result.policies.list;
                this.buildPolicies();
            }, this));
        } else {
            // no policy manager, just one policy (Default Rack)
            rpc.policies = [{
                javaClass: "com.untangle.node.policy.PolicySettings",
                policyId: "1",
                name: i18n._("Default Rack"),
                description: i18n._("The Default Rack/Policy")
            }];
            this.buildPolicies();
        }
    },
    getNodePackageDesc: function(nodeSettings) {
        var i;
        if(this.myApps!==null) {
            for(i=0;i<this.myApps.length;i++) {
                if(this.myApps[i].name==nodeSettings.nodeName) {
                    return this.myApps[i];
                }
            }
        }
        return null;
    },
    createNode: function (nodeProperties, nodeSettings, nodeMetrics, license, runState) {
        var node={};
        node.nodeId=nodeSettings.id;
        node.nodeSettings=nodeSettings;
        node.type=nodeProperties.type;
        node.hasPowerButton=nodeProperties.hasPowerButton;
        node.name=nodeProperties.name;
        node.displayName=nodeProperties.displayName;
        node.license=license;
        node.image='image?name='+node.name;
        node.metrics=nodeMetrics;
        node.runState=runState;
        node.viewPosition=nodeProperties.viewPosition;
        return node;
    },
    buildApps: function () {
        //destroy Apps
        if(main.apps!=null) {
            for(var i=0; i<main.apps.length; i++) {
                Ext.destroy(main.apps[i]);
            }
            this.apps=null;
        }
        //build Apps
        this.apps=[];
        for(var i=0;i<rpc.rackView.applications.list.length;i++) {
            var application=rpc.rackView.applications.list[i];
            var appCmp=new Ung.AppItem(application);
            if(appCmp.isValid) {
                this.apps.push(appCmp);
            }
        }
    },
    findLibItemDisplayName: function(libItemName) {
        if(main.apps!=null) {
            for(var i=0; i<main.apps.length; i++) {
                if(main.apps[i].libItem!=null && main.apps[i].libItem.name==libItemName) {
                  return main.apps[i].libItem.displayName;
                }
            }
        }
        return null;
    },
    buildNodes: function() {
        //build nodes
        Ung.MessageManager.stop();
        Ext.getCmp('policyManagerMenuItem').disable();

        this.destoyNodes();
        this.nodes=[];
        for(var i=0;i<rpc.rackView.instances.list.length;i++) {
            var nodeSettings=rpc.rackView.instances.list[i];
            var nodeProperties=rpc.rackView.nodeProperties.list[i];

            var node=this.createNode(nodeProperties,
                                     nodeSettings,
                                     rpc.rackView.nodeMetrics.map[nodeSettings.id],
                                     rpc.rackView.licenseMap.map[nodeProperties.name],
                                     rpc.rackView.runStates.map[nodeSettings.id]);
            this.nodes.push(node);
        }
        if (this.nodes.length == 0) {
            this.showInitialScreen();
        }
        this.updateSeparator();
        for(var i=0;i<this.nodes.length;i++) {
            var node=this.nodes[i];
            Ext.Function.defer(this.addNode,1, this,[node]);
        }
        if(!main.disableThreads) {
            Ung.MessageManager.start(true);
        }
        if(Ext.MessageBox.isVisible() && Ext.MessageBox.title==i18n._("Please wait")) {
        Ext.Function.defer(Ext.MessageBox.hide,30,Ext.MessageBox);
        }
    },
    // load the rack view for current policy
    loadRackView: function() {
        var callback = Ext.bind(function (result, exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            var parentRackName = this.getParentName( rpc.currentPolicy.parentId );
            var parentRackDisplay = Ext.get('parent-rack-container');

            if (parentRackName === "None") {
                parentRackDisplay.hide();
            } else {
                parentRackDisplay.show();
                parentRackDisplay.dom.innerHTML = i18n._("Parent Rack")+": " + parentRackName;
            }
            
            main.buildApps();
            main.buildNodes();
        }, this);

        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager, [ rpc.currentPolicy.policyId ], callback, 1500, 10 );
    },
    loadApps: function() {
        if(Ung.MessageManager.installInProgress>0) {
            return;
        }
        var callback = Ext.bind(function(result,exception) {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            main.buildApps();
        }, this);

        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager, [ rpc.currentPolicy.policyId ], callback, 1500, 10 );
    },
    loadLicenses: function() {
        //force re-sync with server
        main.getLicenseManager().reloadLicenses();
        var callback = Ext.bind(function(result,exception)
        {
            if(Ung.Util.handleException(exception)) return;
            rpc.rackView=result;
            for (var i = 0; i < main.nodes.length; i++) {
                var nodeCmp = Ung.Node.getCmp(main.nodes[i].nodeId);
                if (nodeCmp && nodeCmp.license) {
                    nodeCmp.updateLicense(rpc.rackView.licenseMap.map[nodeCmp.name]);
                }
            }
        }, this);

        Ung.Util.RetryHandler.retry( rpc.toolboxManager.getRackView, rpc.toolboxManager, [ rpc.currentPolicy.policyId ], callback, 1500, 10 );
    },

    installNode: function(packageDesc, appItem) {
        if(packageDesc===null) {
            return;
        }
        /* Sanity check to see if the node is already installed. */
        node = main.getNode(packageDesc.name);
        if (( node !== null ) && ( node.nodeSettings.policyId == rpc.currentPolicy.policyId )) {
            appItem.hide();
            return;
        }
        
        Ung.AppItem.updateState(packageDesc.displayName, "loadapp");
        main.addNodePreview(packageDesc);
        rpc.nodeManager.instantiateAndStart(Ext.bind(function (result, exception) {
            if(Ung.Util.handleException(exception)) {
                main.removeNodePreview(this.name);
                return;
            }
        },packageDesc), packageDesc.name, rpc.currentPolicy.policyId);
    },
    /**
     *  Returns the reference to the IE window if one exists
     **/         
    getIEWin: function() {
        return this.IEWin;
    },
    getIframeWin: function() {
        if(this.iframeWin==null) {
            this.iframeWin=Ext.create("Ung.Window",{
                id: 'iframeWin',
                title:'',
                layout: 'fit',
                defaults: {},
                items: {
                    html: '<iframe id="iframeWin_iframe" name="iframeWin_iframe" width="100%" height="100%" frameborder="0"/>'
                },
                closeAction:'closeActionFn',
                closeActionFn: function() {
                    this.hide();
                    window.frames["iframeWin_iframe"].location.href="/webui/blank.html";
                    if (this.breadcrumbs) {
                        Ext.destroy(this.breadcrumbs);
                    }
                }
            });
        }
        return this.iframeWin;
    },
    isIframeWinVisible: function() {
        return ((this.iframeWin!=null) && (!this.iframeWin.hidden));
    },
    openInRightFrame: function(title, url) {
        var iframeWin=main.getIframeWin();
        iframeWin.setSizeToRack();
        iframeWin.show();
        if (typeof title == 'string') {
            iframeWin.setTitle(title);
        } else { // the title represents breadcrumbs
          iframeWin.setTitle('<span id="title_' + iframeWin.getId() + '"></span>');
          iframeWin.breadcrumbs = new Ung.Breadcrumbs({
            renderTo: 'title_' + iframeWin.getId(),
            elements: title
          });
        }
        window.frames["iframeWin_iframe"].location.href=url;
    },
    // load Config
    loadConfig: function() {
        this.config =
            [{"name":"networking","displayName":i18n._("Networking"),"iconClass":"icon-config-network","helpSource":"networking_config",handler: main.openNetworking},
            {"name":"administration","displayName":i18n._("Administration"),"iconClass":"icon-config-admin","helpSource":"administration_config", className:"Ung.Administration", scriptFile:"administration.js", handler: main.openConfig},
            {"name":"email","displayName":i18n._("Email"),"iconClass":"icon-config-email","helpSource":"email_config", className:"Ung.Email", scriptFile:"email.js", handler: main.openConfig},
            {"name":"localDirectory","displayName":i18n._("Local Directory"),"iconClass":"icon-config-directory","helpSource":"local_directory_config", className:"Ung.LocalDirectory", scriptFile:"localDirectory.js", handler: main.openConfig},
            {"name":"upgrade","displayName":i18n._("Upgrade"),"iconClass":"icon-config-upgrade","helpSource":"upgrade_config", className:"Ung.Upgrade", scriptFile:"upgrade.js", handler: main.openConfig},
            {"name":"system","displayName":i18n._("System"),"iconClass":"icon-config-setup","helpSource":"system_config", className:"Ung.System", scriptFile:"system.js", handler: main.openConfig},
            {"name":"systemInfo","displayName":i18n._("System Info"),"iconClass":"icon-config-support","helpSource":"system_info_config", className:"Ung.SystemInfo", scriptFile:"systemInfo.js", handler: main.openConfig}];
        this.buildConfig();
    },
    // build config buttons
    buildConfig: function() {
        var out=[];
        for(var i=0;i<this.config.length;i++) {
            var item=this.config[i];
            var appItemCmp=Ext.create('Ung.ConfigItem', {
                item:item
            });
        }
    },
    checkForIE: function (handler) {
        if (Ext.isIE) {
            var noIEDisplay = Ext.get('no-ie-container');
            noIEDisplay.show();

            this.noIEToolTip= new Ext.ToolTip({
                target: document.getElementById("no-ie-container"),
                dismissDelay: 0,
                hideDelay: 1500,
                width: 500,
                cls: 'extended-stats',
                html: i18n._("For an optimal experience use Google Chrome or Mozilla Firefox.")
            });
            this.noIEToolTip.render(Ext.getBody());
        }
    },
    checkForAlerts: function (handler) {
        //check for upgrades
        rpc.alertManager.getAlerts(Ext.bind(function( result, exception, opt, handler ) {
            var alertDisplay = Ext.get('alert-container');
            var alertArr=[];

            if (result != null && result.list.length > 0) {
                alertDisplay.show();
                alertArr.push('<div class="title">'+i18n._("Alerts:")+'</div>');
                for (var i = 0; i < result.list.length; i++) {
                    alertArr.push('<div class="values">&middot;&nbsp;'+i18n._(result.list[i])+'</div>');
                }
            } else {
                alertDisplay.hide();
            }

            this.alertToolTip= new Ext.ToolTip({
                target: document.getElementById("alert-container"),
                dismissDelay: 0,
                hideDelay: 1500,
                width: 500,
                cls: 'extended-stats',
                html: alertArr.join(''),
                items: {
                    xtype: 'button',
                    name: 'Help',
                    iconCls: 'icon-help',
                    text: i18n._('Help with Administration Alerts'),
                    handler: function() {
                        main.openHelp('admin_alerts');
                    }
                }
            });
            this.alertToolTip.render(Ext.getBody());
            
        }, this,[handler],true));
    },
    checkForUpgrades: function (handler) {
        //check for upgrades
        rpc.toolboxManager.getUpgradeStatus(Ext.bind(function(result, exception,opt,handler) {
            main.upgradeLastCheckTime=(new Date()).getTime();
            main.upgradeStatus=result;            
                        
            if(handler) {
                handler.call(this);
            }

            if(Ung.Util.handleException(exception)) return;
            if(main.upgradeStatus!=null && main.upgradeStatus.upgradesAvailable) {
                Ext.getCmp("configItem_upgrade").setIconCls("icon-config-upgrade-available");
            }
        }, this,[handler],true),true);
    },
    openNetworking: function() {
        var alpacaUrl = "/alpaca/";
        var breadcrumbs = [{
            title: i18n._("Configuration"),
            action: Ext.bind(function() {
                main.iframeWin.closeActionFn();
            }, this)
        }, {
            title: i18n._('Networking')
        }];

        main.openInRightFrame(breadcrumbs, alpacaUrl);
    },
    openConfig: function(configItem) {
        Ext.MessageBox.wait(i18n._("Loading Config..."), i18n._("Please wait"));
        Ext.Function.defer(Ung.Util.loadResourceAndExecute,10, this, [configItem.className,Ung.Util.getScriptSrc("script/config/"+configItem.scriptFile), Ext.bind(function() {
            main.configWin = Ext.create(this.className, this);
            main.configWin.show();
            Ext.MessageBox.hide();
        }, configItem)]);
    },
    destoyNodes: function () {
        if(this.nodes!==null) {
            for(var i=0;i<this.nodes.length;i++) {
                var node=this.nodes[i];
                var cmp=Ung.Node.getCmp(this.nodes[i].nodeId);
                if(cmp) {
                    cmp.destroy();
                    cmp=null;
                }
            }
        }
        for(var nodeName in this.nodePreviews) {
            main.removeNodePreview(nodeName);
        }
    },
    getNodePosition: function(place, viewPosition) {
        var placeEl=document.getElementById(place);
        var position=0;
        if(placeEl.hasChildNodes()) {
            for(var i=0;i<placeEl.childNodes.length;i++) {
                if(placeEl.childNodes[i].getAttribute('viewPosition')-viewPosition<0) {
                    position=i+1;
                } else {
                    break;
                }
            }
        }
        return position;
    },
    addNode: function (node, fadeIn) {
        main.removeNodePreview(this.name);
        var nodeWidget=new Ung.Node(node);
        nodeWidget.fadeIn=fadeIn;
        var place=(node.type=="NODE")?'filter_nodes':'service_nodes';
        var position=this.getNodePosition(place,node.viewPosition);
        nodeWidget.render(place,position);
        Ung.AppItem.updateState(node.displayName, null);
        if ( node.name == 'untangle-node-policy') {
            // refresh rpc.policyManager to properly handle the case when the policy manager is removed and then re-added to the application list
            rpc.policyManager=rpc.jsonrpc.UvmContext.nodeManager().node("untangle-node-policy");
            Ext.getCmp('policyManagerMenuItem').enable();
            this.policyNodeWidget = nodeWidget;
        }
    },
    addNodePreview: function (md) {
        var nodeWidget=new Ung.NodePreview(md);
        var place=(md.type=="NODE")?'filter_nodes':'service_nodes';
        var position=this.getNodePosition(place,md.viewPosition);
        nodeWidget.render(place,position);
        main.nodePreviews[md.name]=true;
    },
    removeNodePreview: function(nodeName) {
        if(main.nodePreviews[nodeName]!==undefined)
        delete main.nodePreviews[nodeName];
        var nodePreview=Ext.getCmp("node_preview_"+nodeName);
        if(nodePreview) {
            Ext.destroy(nodePreview);
        }
    },
    removeNode: function(index) {
        var tid = main.nodes[index].nodeId,
        nd,
        nodeUI = tid != null ? Ext.getCmp('node_'+tid): null;
        nd = main.nodes.splice(index, 1);
        delete(nd);
        if(nodeUI) {
            Ext.destroy(nodeUI);
            return true;        
        }
        return false;
    }, 
    getNode: function(nodeName, nodePolicyId) {
        var cp = rpc.currentPolicy.policyId ,np = null;
        if(main.nodes) {
            for (var i = 0; i < main.nodes.length; i++) {
                if(nodePolicyId==null) {
                    cp = null;
                } else {
                    cp = main.nodes[i].nodeSettings.policyId;
                }
            
                if ((nodeName == main.nodes[i].name)&& (nodePolicyId==cp)) {
                    return main.nodes[i];
                }
            }
        }
        return null;
    },
    removeParentNode: function (node, nodePolicyId) {
        var cp = rpc.currentPolicy.policyId;    
        if(main.nodes) {
            for (var i = 0; i < main.nodes.length; i++) {
                if(nodePolicyId==null) {
                    cp = null;
                } else {
                    cp = main.nodes[i].nodeSettings.policyId;
                }
            
                if (node.name === main.nodes[i].name) {
                    if(nodePolicyId!=cp) {
                        //parent found
                        return main.removeNode(i); 
                    }
                }
            }
        }
        return false;        
    },
    isNodeRunning: function(nodeName) {
        var node = main.getNode(nodeName);
        if (node != null) {
             var nodeCmp = Ung.Node.getCmp(node.nodeId);
             if (nodeCmp != null && nodeCmp.isRunning()) {
                return true;
             }
        }
        return false;
    },
    // Show - hide Services header in the rack
    updateSeparator: function() {
        var hasUtil=false;
        var hasService=false;
        for(var i=0;i<this.nodes.length;i++) {
            if(this.nodes[i].type != "NODE") {
                hasService=true;
                if(this.nodes[i].type != "SERVICE") {
                    hasUtil=true;
                }
            }
        }
        document.getElementById("nodes-separator-text").innerHTML=(hasService && hasUtil)?i18n._("Services & Utilities"):hasService?i18n._("Services"):"";
        document.getElementById("nodes-separator").style.display= hasService?"":"none";
        if(hasService) {
            document.getElementById("racks").style.backgroundPosition="0px 100px";
        } else {
            document.getElementById("racks").style.backgroundPosition="0px 50px";
        }
    },
    // build policies select box
    buildPolicies: function () {
        if(main.rackSelect!=null) {
            Ext.destroy(main.rackSelect);
            Ext.get('rack-select-container').dom.innerHTML = '';
        }
        var items=[];
        var selVirtualRackIndex = 0;
        rpc.policyNamesMap = {};
        rpc.policyNamesMap[0] = i18n._("No Rack");
        for( var i=0 ; i<rpc.policies.length ; i++ ) {
            var policy = rpc.policies[i];

            selVirtualRackIndex = (policy.policyId ==1 ? i: selVirtualRackIndex);

            rpc.policyNamesMap[policy.policyId] = policy.name;
            items.push({
                text: policy.name,
                value: policy.policyId,
                index: i,
                handler: main.changeRack,
                hideDelay: 0
            });
            if( policy.policyId == 1 ) {
                rpc.currentPolicy = policy;
            }
        }
        items.push('-');
        items.push({text: i18n._('Show Policy Manager'), value: 'SHOW_POLICY_MANAGER', handler: main.showPolicyManager, id:'policyManagerMenuItem', disabled: true, hideDelay: 0});
        items.push('-');
        items.push({text: i18n._('Show Sessions'), value: 'SHOW_SESSIONS', handler: main.showSessions, hideDelay: 0});
        main.rackSelect = new Ext.SplitButton({
            renderTo: 'rack-select-container', // the container id
            text: items[selVirtualRackIndex].text,
            id:'rack-select',
            menu: new Ext.menu.Menu({
                hideDelay: 0,
                items: items
            })
        });
        this.checkForAlerts();
        this.checkForIE();

        if (this.firstTimeRun) {
            this.checkForUpgrades(main.loadRackView);
        } else {
            main.loadRackView();
            Ext.Function.defer(this.checkForUpgrades,900, this,[null]);
        }

    },
    getPolicyName: function(policyId) {
        if (policyId == null)
            return i18n._( "Services" );

        if (rpc.policyNamesMap[policyId] !== undefined) {
            return rpc.policyNamesMap[policyId];
        } else {
            return i18n._( "Unknown Rack" );
        }
    },
    showSessions: function() {
        main.showNodeSessions(0);
    },
    showNodeSessions: function(nodeIdArg) {
        Ext.MessageBox.wait(i18n._("Loading..."), i18n._("Please wait"));
        if ( main.sessionMonitorWin == null) {
            Ext.Function.defer(Ung.Util.loadResourceAndExecute,10, this,["Ung.SessionMonitor",Ung.Util.getScriptSrc("script/config/sessionMonitor.js"), function() {
                main.sessionMonitorWin=Ext.create('Ung.SessionMonitor', {"name":"sessionMonitor", "helpSource":"session_viewer"});
                main.sessionMonitorWin.setFilterNodeId(nodeIdArg);
                main.sessionMonitorWin.show();
                main.sessionMonitorWin.gridCurrentSessions.reload();
                Ext.MessageBox.hide();
            }]);
        } else {
            Ext.Function.defer(function() {
                main.sessionMonitorWin.setFilterNodeId(nodeIdArg);
                main.sessionMonitorWin.show();
                main.sessionMonitorWin.gridCurrentSessions.reload();
                Ext.MessageBox.hide();
            }, 10, this)
        }
    },
    showPolicyManager: function() {
        if (main.policyNodeWidget) {
            main.policyNodeWidget.loadSettings();
        }
    },
    
    // change current policy
    changeRack: function () {
        Ext.getCmp('rack-select').setText(this.text);
        rpc.currentPolicy=rpc.policies[this.index];
        main.loadRackView();
    },

    getParentName: function( parentId ) {
        if( parentId == null ) {
            return i18n._("None");
        }

        if ( rpc.policies === null ) {
            return i18n._("None");
        }
        
        for ( var c = 0 ; c < rpc.policies.length ; c++ ) {
            if ( rpc.policies[c].policyId == parentId ) {
                return rpc.policies[c].name;
            }
        }
        
        return i18n._("None");
    },
    /**
     *  Prepares the uvm to display the welcome screen
     **/      
    showInitialScreen: function () {
        try {
            Ext.Function.defer(Ext.MessageBox.wait,40,Ext.MessageBox,[i18n._("Determining Connectivity..."), i18n._("Please wait")]);        
            rpc.toolboxManager.isUpgradeServerAvailable(Ext.bind(function (result, exception) {
                if(Ung.Util.handleException(exception)) throw Exception("failure");
                    this.updateInitialScreen(result);
            }, this));
        } catch(e) {
             this.updateInitialScreen(false);
        }
    },
    /**
     * Call back after the upgrade check is made
     */         
    upgradeCheckCallback: function () {
        if(main.upgradeLastCheckTime!=null && (new Date()).getTime()-main.upgradeLastCheckTime<300000 && main.upgradeStatus!=null) {
            if(main.upgradeStatus.upgradesAvailable===true) {
                this.showUpgradeScreen();            
            } else {
                this.showWelcomeScreen();
            }                
        } else {
            this.showWelcomeScreen();
        }
        this.postInitialScreen();                   
    },
    /**
     *  cleanup and ensure the window opened is on the right size
     */         
    postInitialScreen: function () {
        var ifr = main.getIframeWin();
        var position = [];
        var size = main.viewport.getSize();
        var centerSize = Ext.getCmp('center').getSize();
        var centerPosition = Ext.getCmp('center').getPosition();

        ifr.initialConfig.sizeToRack = false;
        ifr.setSize({width:centerSize.width*0.90,height:centerSize.height*0.90});        

        position[0] = centerPosition[0]+Math.round(centerSize.width/20);
        position[1] = centerPosition[1]+Math.round(centerSize.height/20);

        ifr.setPosition(position[0],position[1]);

        Ext.MessageBox.hide();
        Ext.getCmp('center').setSize({width:centerSize.width , height: centerSize.height});                  
    }, 
    /**
     *  Displays the appropriate screen after determining connectivity
     **/     
    updateInitialScreen: function(result) {
        var ifr = main.getIframeWin(),
            position = [],
            size = main.viewport.getSize(),
            centerSize = Ext.getCmp('center').getSize(),
            centerPosition = Ext.getCmp('center').getPosition();
        if(isWizardComplete===true) {
            if(result===true) {
                main.checkForUpgrades(this.upgradeCheckCallback);
                return;
            } else {
                this.showFailureScreen();        
            }        
        } else {
            this.showRunSetupScreen();
        }
        this.postInitialScreen();          
    },
    /**
     *  Displays the run setup first screen
     **/         
    showRunSetupScreen: function() {
        this.openWindow( "", "runsetup", i18n._("Welcome"), "/webui/runsetup.jsp");
    },     
    /**
     *  Displays the offline welcome screen
     **/             
    showFailureScreen: function () {
        this.openStore("offline",i18n._("Welcome"));
    },
    /**
     *  Displays the online welcome screen
     **/         
    showWelcomeScreen: function () {
        this.openStore("online-welcome",i18n._("Welcome"));            
    },
    /**
     * Display the upgrade screen
     */
     showUpgradeScreen: function() {
        this.openStore('upgrade',i18n._("Welcome"));
     },
    /**
     *  Displays the setup wizard
     **/         
    showSetupWizardScreen: function() {
        this.openWindow( "", "setupwizard", i18n._("Setup Wizard"), "/setup/");
    },     
    /**
     *  Hides the welcome screen
     */         
    hideWelcomeScreen: function() {
        var win = null;
        if(Ext.isIE===true) {
            win = this.IEWin;
            if(win != null) {
                win.close();
                this.IEWin = null;
            }
        } else {
            win = main.getIframeWin();
            if(win != null) {
                win.closeActionFn();
            }            
        }        
    }
});