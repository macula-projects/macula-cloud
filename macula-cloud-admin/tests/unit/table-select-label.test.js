import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import ScTableSelect from "@/components/scTableSelect/index.vue";
import { nextTick } from "vue";

describe("table select labels with current Element Plus", () => {
  it.each([true, false])("renders object labels with multiple=%s without legacy selected access", async multiple => {
    const user = { username: "admin", nickname: "负责人" };
    const wrapper = mount(ScTableSelect, {
      props: { modelValue: multiple ? [user] : user, multiple, props: { label: "nickname", value: "username" } },
      global: { plugins: [ElementPlus] }
    });
    await nextTick();
    await nextTick();
    expect(wrapper.text()).toContain("负责人");
    wrapper.unmount();
    await nextTick();
  });
});
