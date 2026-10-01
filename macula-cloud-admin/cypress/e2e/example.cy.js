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
});
