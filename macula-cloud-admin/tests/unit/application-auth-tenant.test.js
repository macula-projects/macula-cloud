import { describe, expect, it, vi } from "vitest";
import ApplicationSave from "@/views/system/application/save.vue";

describe("application auth tenant", () => {
  it("loads named tenant options and keeps ids as strings", async () => {
    const get = vi.fn().mockResolvedValue({ success: true, data: [
      { label: "Macula Cloud", value: 1 },
      { label: "Macula Samples", value: "9007199254740993" }
    ] });
    const vm = { visible: true, tenantOptions: [], tenantOptionsLoading: false,
      $API: { system_tenant: { tenant: { options: { get } } } } };
    await ApplicationSave.methods.loadTenantOptions.call(vm);
    expect(get).toHaveBeenCalledWith({ filterMe: 1 });
    expect(vm.tenantOptions).toEqual([
      { label: "Macula Cloud", value: "1" },
      { label: "Macula Samples", value: "9007199254740993" }
    ]);
    expect(vm.tenantOptionsLoading).toBe(false);
  });

  it("loads options when opening the application editor", () => {
    const vm = { form: {}, loadTenantOptions: vi.fn() };
    ApplicationSave.methods.open.call(vm, "edit");
    expect(vm.loadTenantOptions).toHaveBeenCalledOnce();
  });

  it("preserves a long tenant id as a string when editing", () => {
    const vm = { form: {}, getUsersByUsernames: vi.fn() };
    ApplicationSave.methods.setData.call(vm, { authTenantId: "9007199254740993" });
    expect(vm.form.authTenantId).toBe("9007199254740993");
    ApplicationSave.methods.setData.call(vm, { authTenantId: null });
    expect(vm.form.authTenantId).toBe("");
  });

  it.each([
    ["7", "7", undefined],
    ["12", "7", "12"],
    ["", "7", null]
  ])("preserves an unchanged auth tenant while allowing explicit edits", async (current, original, expected) => {
    const put = vi.fn().mockResolvedValue({ success: true });
    let submitted;
    const vm = {
      form: { id: "34", applicationName: "Renamed application", authTenantId: current, manager: [{ username: "admin" }] },
      originalAuthTenantId: original,
      mode: "edit", $emit: vi.fn(), visible: true,
      $refs: { dialogForm: { validate: callback => { submitted = callback(true); } } },
      $API: { system_application: { application: { edit: { put } } } }
    };
    ApplicationSave.methods.submit.call(vm);
    await submitted;
    const payload = put.mock.calls[0][0];
    expect(payload.applicationName).toBe("Renamed application");
    if (expected === undefined) expect(payload).not.toHaveProperty("authTenantId");
    else expect(payload.authTenantId).toBe(expected);
  });

  it.each([["", null], [null, null], [undefined, null], ["9007199254740993", "9007199254740993"]])(
    "submits %s as an explicit value without numeric conversion", async (input, expected) => {
      const post = vi.fn().mockResolvedValue({ success: true });
      let submitted;
      const vm = {
        form: { authTenantId: input, manager: [{ username: "admin" }] },
        mode: "add", $emit: vi.fn(), visible: true,
        $refs: { dialogForm: { validate: callback => { submitted = callback(true); } } },
        $API: { system_application: { application: { add: { post } } } }
      };
      ApplicationSave.methods.submit.call(vm);
      await submitted;
      expect(post).toHaveBeenCalledWith(expect.objectContaining({ authTenantId: expected }));
    }
  );
});
