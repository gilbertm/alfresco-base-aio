<#-- Custom CUD Footer Template -->
<div class="cud-footer">
    <div class="cud-footer-left">
        &copy; ${.now?string('yyyy')} Canadian University Dubai. All rights reserved.
    </div>
    <div class="cud-footer-right">
        <span>EDMS v1.0</span>
        <span class="cud-footer-sep">|</span>
        <span>Alfresco Community ${server.edition} ${server.version}</span>
    </div>
</div>

<style>
.cud-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #003366;
    color: #cccccc;
    padding: 10px 20px;
    font-size: 12px;
    border-top: 3px solid #c8102e;
}
.cud-footer-sep {
    margin: 0 8px;
}
</style>