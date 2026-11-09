package com.invoiceapproval.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the HTTP contract the static UI (app.js) and the e2e tests rely on: routes, status codes
 * and JSON shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return "Bearer " + node.get("token").asText();
    }

    @Test
    void staticUiIsServedWithoutSignIn() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresASession() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
    }

    @Test
    void wrongPasswordIs401WithAnErrorMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo.approver\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password."));
    }

    @Test
    void listReturnsSeededInvoicesInTheShapeTheUiExpects() throws Exception {
        mvc.perform(get("/api/invoices").header("Authorization", login("demo.approver")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("INV-1001"))
                .andExpect(jsonPath("$[0].customerName").value("Lakeside School District"))
                .andExpect(jsonPath("$[0].amount").value(4250.00))
                .andExpect(jsonPath("$[0].status").value("PendingApproval"));
    }

    @Test
    void approveThenApproveAgainIs200Then409() throws Exception {
        String token = login("demo.approver");

        mvc.perform(post("/api/approvals/INV-1001/approve").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Approved"))
                .andExpect(jsonPath("$.decidedBy").value("demo.approver"));

        mvc.perform(post("/api/approvals/INV-1001/approve").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void approvingAnUnknownInvoiceIs404() throws Exception {
        mvc.perform(post("/api/approvals/INV-404/approve").header("Authorization", login("demo.admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectingWithoutAReasonIs400() throws Exception {
        mvc.perform(post("/api/approvals/INV-1002/reject").header("Authorization", login("demo.approver"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteIsAdminOnly() throws Exception {
        mvc.perform(delete("/api/invoices/INV-1005").header("Authorization", login("demo.approver")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/invoices/INV-1005").header("Authorization", login("demo.admin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void createValidatesAndReturns201WithLocation() throws Exception {
        String token = login("demo.approver");

        mvc.perform(post("/api/invoices").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"  \",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Customer name is required."));

        mvc.perform(post("/api/invoices").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\" Pine Ridge \",\"amount\":125.50}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/invoices/INV-1006"))
                .andExpect(jsonPath("$.customerName").value("Pine Ridge"))
                .andExpect(jsonPath("$.status").value("Draft"));
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mvc.perform(get("/api/invoices").param("status", "Paid").header("Authorization", login("demo.approver")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown status 'Paid'."));
    }
}
