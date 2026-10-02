import {describe, expect, it} from "vitest"

import config, {mergeRuntimeConfig} from "@/config"

describe("admin login configuration", () => {
    it("loads the local demo defaults", () => {
        expect(config).toMatchObject({
            OAUTH_CLIENT_ID: "e4da4a32-592b-46f0-ae1d-784310e88423",
            OAUTH_CLIENT_SECRET: "secret",
            OAUTH_SCOPE: "message.read message.write userinfo",
            DEMO_USERNAME: "admin",
            DEMO_PASSWORD: "admin"
        })
    })

    it("applies runtime configuration last", () => {
        const target = {
            OAUTH_CLIENT_ID: "build-client",
            DEMO_USERNAME: "build-user"
        }

        expect(mergeRuntimeConfig(target, {
            OAUTH_CLIENT_ID: "runtime-client",
            DEMO_USERNAME: "runtime-user"
        })).toEqual({
            OAUTH_CLIENT_ID: "runtime-client",
            DEMO_USERNAME: "runtime-user"
        })
    })
})
