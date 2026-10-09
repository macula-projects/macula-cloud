(() => {
  "use strict";
  document.querySelectorAll("[data-reveal]").forEach(button => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.reveal);
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.textContent = visible ? "隐藏" : "显示";
      button.setAttribute("aria-pressed", String(visible));
    });
  });
  document.querySelectorAll("[data-tab]").forEach(tab => {
    tab.addEventListener("click", () => {
      document.querySelectorAll("[data-tab]").forEach(item => item.setAttribute("aria-selected", String(item === tab)));
      document.querySelectorAll("[data-login]").forEach(form => { form.hidden = form.id !== tab.dataset.tab; });
      document.getElementById(tab.dataset.tab).querySelector("input:not([type=hidden])")?.focus();
    });
  });
  document.querySelectorAll("[data-login]").forEach(form => {
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (form.dataset.busy || !form.reportValidity()) return;
      const button = form.querySelector("[type=submit]");
      const error = form.querySelector("[role=alert]");
      const caption = button.textContent;
      const body = new URLSearchParams(new FormData(form));
      form.dataset.busy = "true"; button.disabled = true; button.textContent = "正在验证…";
      form.setAttribute("aria-busy", "true"); error.textContent = "";
      const timeout = new AbortController();
      const timer = window.setTimeout(() => timeout.abort(), 20000);
      try {
        const response = await fetch(form.action, { method: "POST", body, credentials: "same-origin",
          headers: { "Accept": "application/json" }, signal: timeout.signal });
        const result = await response.json().catch(() => null);
        if (!response.ok || !result?.success || !result?.data?.targetUrl) {
          throw new Error(response.status === 403 ? "页面已过期，请刷新后重试。" :
            response.status >= 500 ? "暂时无法登录，请稍后重试。" : "登录失败，请检查账号和密码后重试。");
        }
        const destination = new URL(result.data.targetUrl, window.location.href);
        if (destination.origin !== window.location.origin) throw new Error("无法恢复登录请求，请从应用重新发起登录。");
        window.location.assign(destination.href);
      } catch (failure) {
        error.textContent = failure.name === "AbortError" ? "请求超时，请检查网络后重试。" :
          failure instanceof TypeError ? "网络连接失败，请检查网络后重试。" : failure.message;
        error.focus();
      } finally {
        clearTimeout(timer); delete form.dataset.busy; button.disabled = false; button.textContent = caption;
        form.removeAttribute("aria-busy");
      }
    });
  });
  const consent = document.querySelector("[data-consent]");
  if (consent) {
    consent.addEventListener("submit", event => {
      if (consent.dataset.busy) { event.preventDefault(); return; }
      if (event.submitter?.value === "deny")
        consent.querySelectorAll("input[name=scope]").forEach(input => { input.disabled = true; });
      consent.dataset.busy = "true";
      consent.setAttribute("aria-busy", "true");
      consent.querySelectorAll("button").forEach(button => { button.setAttribute("aria-disabled", "true"); });
    });
    window.addEventListener("pageshow", () => {
      delete consent.dataset.busy; consent.removeAttribute("aria-busy");
      consent.querySelectorAll("button").forEach(button => button.removeAttribute("aria-disabled"));
      consent.querySelectorAll("input[name=scope]").forEach(input => { input.disabled = false; });
    });
  }
})();
