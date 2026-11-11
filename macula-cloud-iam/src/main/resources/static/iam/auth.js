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
            document.querySelectorAll("[data-tab]").forEach(item => {
                item.setAttribute("aria-selected", String(item === tab));
                item.tabIndex = item === tab ? 0 : -1;
            });
            document.querySelectorAll("[data-login]").forEach(form => {
                form.hidden = form.id !== tab.dataset.tab;
            });
            document.getElementById(tab.dataset.tab)?.querySelector("input:not([type=hidden])")?.focus();
        });
    });
    const tabs = Array.from(document.querySelectorAll("[data-tab]"));
    tabs.forEach((tab, index) => tab.addEventListener("keydown", event => {
        let next;
        if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
        else if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
        else if (event.key === "Home") next = 0;
        else if (event.key === "End") next = tabs.length - 1;
        else return;
        event.preventDefault();
        tabs[next].click();
        tabs[next].focus();
    }));
    document.querySelectorAll("[data-login]").forEach(form => {
        form.addEventListener("submit", event => {
            if (form.dataset.busy) {
                event.preventDefault();
                return;
            }
            form.dataset.busy = "true";
            form.setAttribute("aria-busy", "true");
            form.querySelectorAll("button[type=submit]").forEach(button => {
                button.disabled = true;
            });
        });
        window.addEventListener("pageshow", () => {
            delete form.dataset.busy;
            form.removeAttribute("aria-busy");
            form.querySelectorAll("button[type=submit]").forEach(button => {
                button.disabled = false;
            });
        });
    });
    document.querySelectorAll("[data-send-code]").forEach(button => {
        button.addEventListener("click", async () => {
            const form = button.closest("form");
            const target = form.querySelector(`[name="${button.dataset.codeTarget || "phone"}"]`);
            const csrf = form.querySelector("input[type=hidden]");
            const error = form.querySelector("[role=alert]");
            if (!target || !target.reportValidity() || button.disabled) return;
            const body = new URLSearchParams({[target.name]: target.value});
            if (csrf) body.set(csrf.name, csrf.value);
            button.disabled = true;
            error.textContent = "";
            try {
                if (button.hasAttribute("data-binding-code")) {
                    body.set("username", form.querySelector("[name=username]")?.value || "");
                }
                const response = await fetch(button.dataset.sendCodeUrl, {
                    method: "POST",
                    body,
                    credentials: "same-origin"
                });
                if (!response.ok) throw new Error("验证码暂时无法发送，请稍后重试。");
                let remaining = 60;
                button.textContent = `${remaining} 秒后重试`;
                const timer = window.setInterval(() => {
                    remaining -= 1;
                    button.textContent = remaining ? `${remaining} 秒后重试` : "获取验证码";
                    if (!remaining) {
                        clearInterval(timer);
                        button.disabled = false;
                    }
                }, 1000);
            } catch (failure) {
                error.textContent = failure.message;
                button.disabled = false;
            }
        });
    });
    const consent = document.querySelector("[data-consent]");
    if (consent) {
        consent.addEventListener("submit", event => {
            if (consent.dataset.busy) {
                event.preventDefault();
                return;
            }
            if (event.submitter?.value === "deny")
                consent.querySelectorAll("input[name=scope]").forEach(input => {
                    input.disabled = true;
                });
            consent.dataset.busy = "true";
            consent.setAttribute("aria-busy", "true");
            consent.querySelectorAll("button").forEach(button => {
                button.setAttribute("aria-disabled", "true");
            });
        });
        window.addEventListener("pageshow", () => {
            delete consent.dataset.busy;
            consent.removeAttribute("aria-busy");
            consent.querySelectorAll("button").forEach(button => button.removeAttribute("aria-disabled"));
            consent.querySelectorAll("input[name=scope]").forEach(input => {
                input.disabled = false;
            });
        });
    }
})();
