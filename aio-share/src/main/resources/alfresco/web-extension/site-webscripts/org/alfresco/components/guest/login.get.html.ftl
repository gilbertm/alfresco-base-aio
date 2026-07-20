<#-- Custom CUD Login Component Template -->
<style>
    body {
        background: url("https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=1920&q=80") no-repeat center center fixed !important;
        background-size: cover !important;
        font-family: "Segoe UI", Arial, sans-serif;
    }
    body::before {
        content: "";
        position: fixed;
        top: 0; left: 0; right: 0; bottom: 0;
        background: rgba(0, 20, 50, 0.75);
        z-index: -1;
    }
    /* Hide default Alfresco login elements */
    .theme-company-logo, .login-panel, .login-copy, .login-logo {
        display: none !important;
    }
    .cud-login-container {
        max-width: 440px;
        margin: 80px auto;
        padding: 40px;
        background: #ffffff;
        border-radius: 8px;
        box-shadow: 0 8px 32px rgba(0,0,0,0.3);
        text-align: center;
        position: relative;
        z-index: 1;
    }
    .cud-login-logo {
        width: 180px;
        margin-bottom: 24px;
    }
    .cud-login-title {
        font-size: 22px;
        font-weight: 700;
        color: #003366;
        margin-bottom: 8px;
    }
    .cud-login-subtitle {
        font-size: 14px;
        color: #666666;
        margin-bottom: 32px;
    }
    .cud-login-form label {
        display: none;
    }
    .cud-login-form input[type="text"],
    .cud-login-form input[type="password"] {
        width: 100%;
        padding: 12px;
        margin-bottom: 16px;
        border: 1px solid #cccccc;
        border-radius: 4px;
        font-size: 14px;
        box-sizing: border-box;
    }
    .cud-login-form input[type="submit"] {
        width: 100%;
        padding: 12px;
        background-color: #c8102e;
        color: #ffffff;
        border: none;
        border-radius: 4px;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
    }
    .cud-login-form input[type="submit"]:hover {
        background-color: #e01234;
    }
    .cud-login-footer {
        margin-top: 24px;
        font-size: 12px;
        color: #999999;
    }
</style>

<div class="cud-login-container">
    <img src="${url.context}/res/themes/CUDCustomTheme/images/app-logo.png"
         alt="Canadian University Dubai" class="cud-login-logo" />
    <div class="cud-login-title">Enterprise Document Management</div>
    <div class="cud-login-subtitle">Sign in to access the EDMS portal</div>

    <form id="loginform" method="post"
          action="${url.context}/page/dologin" class="cud-login-form">
        <input type="text" id="username" name="username"
               placeholder="Username" autocomplete="username" />
        <input type="password" id="password" name="password"
               placeholder="Password" autocomplete="current-password" />
        <input type="hidden" name="success" value="${url.context}/page/site-index" />
        <input type="hidden" name="failure" value="${url.context}/page?error=true" />
        <input type="submit" value="Sign In" />
    </form>

    <div class="cud-login-footer">
        &copy; ${.now?string('yyyy')} Canadian University Dubai
    </div>
</div>
