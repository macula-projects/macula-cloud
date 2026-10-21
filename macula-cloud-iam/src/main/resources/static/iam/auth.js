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
  document.querySelectorAll("[data-send-code]").forEach(button => {
    button.addEventListener("click", async () => {
      const form = button.closest("form");
      const phone = form.querySelector("[name=phone]");
      const csrf = form.querySelector("input[type=hidden]");
      const error = form.querySelector("[role=alert]");
      if (!phone.reportValidity() || button.disabled) return;
      const body = new URLSearchParams({ phone: phone.value });
      if (csrf) body.set(csrf.name, csrf.value);
      button.disabled = true; error.textContent = "";
      try {
        const response = await fetch(button.dataset.sendCodeUrl, { method: "POST", body, credentials: "same-origin" });
        if (!response.ok) throw new Error("验证码暂时无法发送，请稍后重试。");
        let remaining = 60; button.textContent = `${remaining} 秒后重试`;
        const timer = window.setInterval(() => {
          remaining -= 1; button.textContent = remaining ? `${remaining} 秒后重试` : "获取验证码";
          if (!remaining) { clearInterval(timer); button.disabled = false; }
        }, 1000);
      } catch (failure) { error.textContent = failure.message; button.disabled = false; }
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
