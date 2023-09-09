// Header text
Ext.define("NX.app.PluginStrings", {
  "@aggregate_priority": 90,
  singleton: true,
  requires: ["NX.I18n"],
  keys: {
      Header_Panel_Logo_Text: "Nexus OSS",
  }
}, function(a) {
  NX.I18n.register(a)
});

// Login dialog
Ext.define("NX.view.SignIn", {
  extend: "NX.view.ModalDialog",
  alias: "widget.nx-signin",
  requires: ["NX.I18n"],
  initComponent: function() {
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
                  html: "<div>Sign in with SSO</div>",
                  ui: "nx-primary",
                  iconCls : 'x-fa fa-sign-in-alt',
                  id : 'signInSSO',
                  width: '100%',
                  tooltip : 'SSO Login',
                  handler: function() {
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
          resize: function() {
              a.down("#username").focus()
          },
          single: true
      });
      a.callParent()
  },
  addMessage: function(b) {
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
  clearMessage: function() {
      var a = this
        , b = a.down("#signinMessage");
      if (b) {
          a.down("form").remove(b)
      }
  }
});

// Api key dialog (Accessing NuGet API Key)
Ext.define("NX.view.Authenticate", {
  extend: "NX.view.ModalDialog",
  alias: "widget.nx-authenticate",
  requires: ["NX.Icons", "NX.I18n"],
  cls: "nx-authenticate",
  message: undefined,
  initComponent: function() {
      var a = this;
      a.ui = "nx-inset";
      a.title = NX.I18n.get("Authenticate_Title");
      a.setWidth(NX.view.ModalDialog.MEDIUM_MODAL);
      // SSO description
      a.message = '<div>Accessing API Key requires validation of your credentials (<strong>enter your username if using SSO login</strong>).</div>';
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
                  handler: function() {
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
          resize: function() {
              a.down("#password").focus()
          },
          single: true
      });
      a.callParent()
  }
});

// document.querySelector("#treeview-1021-record-708 > tbody > tr > td > div > span").textContent = "Api Key"
// document.querySelector("#nx-coreui-react-main-container-1172 > main > div.nxrm-page-header > div > h1 > span").textContent = "Api Key"