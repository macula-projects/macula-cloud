import { describe, expect, it } from "vitest";
import tool from "@/utils/tool";

describe("接口ISO时间的当地显示", () => {
  it("不同偏移表示同一时刻，并按浏览器当地时区跨日展示", () => {
    const utc = "2026-11-04T23:50:30.000Z";
    expect(tool.formatDateTime(utc)).toBe(new Date(utc).toLocaleString("sv-SE", { hour12: false }));
    expect(tool.formatDateTime("2026-11-05T07:50:30.000+08:00")).toBe(tool.formatDateTime(utc));
  });
  it("Instant纳秒ISO输入与Date毫秒ISO显示同一时刻", () => {
    expect(tool.formatDateTime("2026-11-05T00:50:30.123456789Z"))
      .toBe(tool.formatDateTime("2026-11-05T00:50:30.123Z"));
  });
  it.each([null, undefined, "", "invalid"])("空值或非法值不显示1970年：%s", value => {
    expect(tool.formatDateTime(value)).toBe("—");
  });
  it("合法的零时间戳仍可正常格式化", () => {
    expect(tool.formatDateTime(0)).toBe(tool.dateFormat(0));
  });
});
