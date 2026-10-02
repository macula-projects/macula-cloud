/*
 * Copyright (c) 2023 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// https://docs.cypress.io/api/introduction/api.html

describe("Admin login", () => {
  it("redirects the app root to the login page", () => {
    cy.visit("/");
    cy.location("hash").should("eq", "#/login");
    cy.contains("账号登录").should("be.visible");
    cy.contains("button", "登录").should("be.visible");
  });

  it("shows the IAM response message when password authentication fails", () => {
    cy.intercept("POST", "**/oauth2/token", {
      statusCode: 400,
      body: {
        error_description: "Bad credentials",
        error: "bad_credentials",
      },
    }).as("tokenRequest");

    cy.visit("/#/login");
    cy.contains("button", "登录").click();

    cy.wait("@tokenRequest");
    cy.contains(".el-message", "Bad credentials").should("be.visible");
    cy.contains("button", "登录").should("not.be.disabled");
  });

  it("uses runtime OAuth and demo account configuration", () => {
    cy.intercept("GET", "**/config.js*", {
      headers: {"content-type": "application/javascript"},
      body: `const APP_CONFIG = {
        OAUTH_CLIENT_ID: "runtime-client",
        OAUTH_CLIENT_SECRET: "runtime-secret",
        OAUTH_SCOPE: "runtime.scope",
        DEMO_USERNAME: "runtime-admin",
        DEMO_PASSWORD: "runtime-password"
      }`,
    });
    cy.intercept("POST", "**/oauth2/token", (request) => {
      const body = new URLSearchParams(request.body);
      expect(body.get("username")).to.eq("runtime-admin");
      expect(body.get("password")).to.eq("runtime-password");
      expect(body.get("client_id")).to.eq("runtime-client");
      expect(body.get("client_secret")).to.eq("runtime-secret");
      expect(body.get("scope")).to.eq("runtime.scope");
      request.reply({
        statusCode: 400,
        body: {error_description: "Runtime credentials checked"},
      });
    }).as("runtimeTokenRequest");

    cy.visit("/#/login");
    cy.get('input[type="text"]').first().should("have.value", "runtime-admin");
    cy.get('input[type="password"]').should("have.value", "runtime-password");
    cy.contains("button", "登录").click();

    cy.wait("@runtimeTokenRequest");
    cy.contains(".el-message", "Runtime credentials checked").should("be.visible");
  });
});
