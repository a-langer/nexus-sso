// Velocity template variable see in com.github.alanger.nexus.plugin.resources.UiPac4jPluginDescriptor
//
// Header_Panel_Logo_Text: "$Header_Panel_Logo_Text"
// SignIn_Modal_Dialog_Html: "$SignIn_Modal_Dialog_Html"
// SignIn_Modal_Dialog_Tooltip: "$SignIn_Modal_Dialog_Tooltip"
// SignIn_SSO_Enabled: "$SignIn_SSO_Enabled"
// Authenticate_Modal_Dialog_Message: "$Authenticate_Modal_Dialog_Message"

// Wait all plugins
var checkExist = setInterval(function () {
  var done = ((typeof NX != "undefined") && (typeof NX.I18n != "undefined")
    && (typeof NX.app != "undefined"));
  // console.log("! checkExist done: " + done);
  if (done) {
    clearInterval(checkExist);
    initHeader();
    if ($SignIn_SSO_Enabled || false) {
      initLoginDialog();
      initApiKeyDialog();
    }
  }
}, 10);

function initHeader() {
  console.log("SSO Header");
  // Header text, see components/nexus-rapture/src/main/resources/static/rapture/NX/app/PluginStrings.js
  Ext.define("NX.app.PluginStrings", {
    "@aggregate_priority": 90,
    singleton: true,
    requires: ["NX.I18n"],
    keys: {
      Header_Panel_Logo_Text: "$Header_Panel_Logo_Text",
    }
  }, function (a) {
    NX.I18n.register(a)
  });
}

function initLoginDialog() {
  console.log("SSO Login dialog");
  // Login dialog
  Ext.define("NX.view.SignIn", {
    extend: "NX.view.ModalDialog",
    alias: "widget.nx-signin",
    requires: ["NX.I18n"],
    initComponent: function () {
      var a = this;
      a.ui = "nx-inset";
      a.title = NX.I18n.get("SignIn_Title");
      a.setWidth(NX.view.ModalDialog.SMALL_MODAL);
      Ext.apply(a, {
        items: {
          xtype: "form",
          defaultType: "textfield",
          defaults: {
            anchor: "100%"
          },
          items: [
            {   // SSO login button
              xtype: "button",
              height: 40,
              html: "$SignIn_Modal_Dialog_Html",
              ui: "nx-primary",
              iconCls: 'x-fa fa-sign-in-alt',
              id: 'signInSSO',
              width: '100%',
              tooltip: '$SignIn_Modal_Dialog_Tooltip',
              handler: function () {
                window.location.href = "./index.html";
              }
            }, { // SSO login separator
              xtype: "label",
              width: '100%',
              html: '<div style="text-align:center;">or</div>'
            }, {
              name: "username",
              itemId: "username",
              emptyText: NX.I18n.get("SignIn_Username_Empty"),
              allowBlank: false,
              validateOnBlur: false
            }, {
              name: "password",
              itemId: "password",
              inputType: "password",
              emptyText: NX.I18n.get("SignIn_Password_Empty"),
              allowBlank: false,
              validateOnBlur: false
            }],
          buttonAlign: "left",
          buttons: [{
            text: NX.I18n.get("SignIn_Submit_Button"),
            action: "signin",
            formBind: true,
            bindToEnter: true,
            ui: "nx-primary"
          }, {
            text: NX.I18n.get("SignIn_Cancel_Button"),
            handler: a.close,
            scope: a
          }]
        }
      });
      a.on({
        resize: function () {
          a.down("#username").focus()
        },
        single: true
      });
      a.callParent()
    },
    addMessage: function (b) {
      var a = this
        , d = '<div id="signin-message">' + b + "</div><br>"
        , c = a.down("#signinMessage");
      if (c) {
        c.html(d)
      } else {
        a.down("form").insert(0, {
          xtype: "component",
          itemId: "signinMessage",
          html: d
        })
      }
    },
    clearMessage: function () {
      var a = this
        , b = a.down("#signinMessage");
      if (b) {
        a.down("form").remove(b)
      }
    }
  });
}

function initApiKeyDialog() {
  console.log("SSO Api key dialog");
  // Api key dialog (Accessing NuGet API Key)
  Ext.define("NX.view.Authenticate", {
    extend: "NX.view.ModalDialog",
    alias: "widget.nx-authenticate",
    requires: ["NX.Icons", "NX.I18n"],
    cls: "nx-authenticate",
    message: undefined,
    initComponent: function () {
      var a = this;
      a.ui = "nx-inset";
      a.title = NX.I18n.get("Authenticate_Title");
      a.setWidth(NX.view.ModalDialog.MEDIUM_MODAL);
      // SSO description
      a.message = '$Authenticate_Modal_Dialog_Message';
      Ext.apply(this, {
        closable: false,
        items: {
          xtype: "form",
          defaultType: "textfield",
          defaults: {
            anchor: "100%"
          },
          items: [{
            xtype: "container",
            layout: "hbox",
            cls: "message",
            items: [{
              xtype: "component",
              html: NX.Icons.img("authenticate", "x32")
            }, {
              xtype: "label",
              height: 48,
              html: "<div>" + a.message + "</div>"
            }]
          }, {
            name: "username",
            itemId: "username",
            emptyText: NX.I18n.get("SignIn_Username_Empty"),
            allowBlank: false,
            readOnly: true
          }, {
            name: "password",
            itemId: "password",
            inputType: "password",
            emptyText: NX.I18n.get("SignIn_Password_Empty"),
            allowBlank: false,
            validateOnBlur: false
          }],
          buttonAlign: "left",
          buttons: [{
            text: NX.I18n.get("User_View_Authenticate_Submit_Button"),
            action: "authenticate",
            formBind: true,
            bindToEnter: true,
            ui: "nx-primary"
          }, {
            text: NX.I18n.get("Authenticate_Cancel_Button"),
            handler: function () {
              if (!!a.options && Ext.isFunction(a.options.failure)) {
                a.options.failure.call(a.options.failure, a.options)
              }
              a.close()
            },
            scope: a
          }]
        }
      });
      a.on({
        resize: function () {
          a.down("#password").focus()
        },
        single: true
      });
      a.callParent()
    }
  });
}
