package com.example.wallet_system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet_system.dto.request.LoginRequest;
import com.example.wallet_system.enums.Role;
import com.example.wallet_system.repository.TransactionRepository;
import com.example.wallet_system.repository.UserRepository;
import com.example.wallet_system.repository.WalletRepository;
import com.example.wallet_system.support.TestFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack API tests through MockMvc: real security filter chain, real DB.
 * Covers auth, wallet operations, authorization boundaries and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String register(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, password));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        return login(email, password);
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, password));
        MvcResult result = mockMvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void registerThenLoginFlow() throws Exception {
        String token = register("flow@test.local", "Password123");
        assertThat(token).isNotBlank();
    }

    @Test
    void duplicateRegistrationConflicts() throws Exception {
        register("dup@test.local", "Password123");
        String body = objectMapper.writeValueAsString(new LoginRequest("dup@test.local", "Password123"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", is("DUPLICATE_EMAIL")));
    }

    @Test
    void invalidRegistrationRejected() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("not-an-email", "short"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void invalidLoginRejected() throws Exception {
        register("login@test.local", "Password123");
        String body = objectMapper.writeValueAsString(new LoginRequest("login@test.local", "WrongPass1"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    void addMoneyThenViewWallet() throws Exception {
        String token = register("add@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "add-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 500.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("ADD")))
            .andExpect(jsonPath("$.status", is("SUCCESS")));

        mockMvc.perform(get("/wallet").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance", is(500.00)));
    }

    @Test
    void addMoneyInvalidAmountRejected() throws Exception {
        String token = register("negamt@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "add-neg")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": -5.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    void addMoneyMissingIdempotencyKeyRejected() throws Exception {
        String token = register("nokey@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("MISSING_IDEMPOTENCY_KEY")));
    }

    @Test
    void addMoneyWithoutAuthRejected() throws Exception {
        mockMvc.perform(post("/wallet/add")
                .header("Idempotency-Key", "no-auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5.00}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    void invalidJwtRejected() throws Exception {
        mockMvc.perform(get("/wallet").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void transactionHistoryIsPaginated() throws Exception {
        String token = register("hist@test.local", "Password123");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/wallet/add")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "h-" + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 10.00}"))
                .andExpect(status().isOk());
        }
        mockMvc.perform(get("/wallet/transactions?page=0&size=2")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()", is(2)))
            .andExpect(jsonPath("$.totalElements", is(3)))
            .andExpect(jsonPath("$.totalPages", is(2)))
            .andExpect(jsonPath("$.first", is(true)))
            .andExpect(jsonPath("$.last", is(false)));
    }

    @Test
    void transferBetweenUsers() throws Exception {
        String senderToken = register("s@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "seed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000.00}"))
            .andExpect(status().isOk());
        var receiver = fixtures.createUser("r@test.local", "Password123", Role.USER, BigDecimal.ZERO);

        mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "xfer-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toUserId\": " + receiver.getId() + ", \"amount\": 300.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("TRANSFER")));
    }

    @Test
    void duplicateIdempotentTransferReturnsSameResult() throws Exception {
        String senderToken = register("s2@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "seed2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000.00}"))
            .andExpect(status().isOk());
        var receiver = fixtures.createUser("r2@test.local", "Password123", Role.USER, BigDecimal.ZERO);

        String req = "{\"toUserId\": " + receiver.getId() + ", \"amount\": 300.00}";
        MvcResult first = mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "xfer-dup")
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "xfer-dup")
                .contentType(MediaType.APPLICATION_JSON).content(req))
            .andExpect(status().isOk()).andReturn();

        long id1 = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        long id2 = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void insufficientBalanceTransferRejected() throws Exception {
        String senderToken = register("poor@test.local", "Password123");
        var receiver = fixtures.createUser("rich-recv@test.local", "Password123", Role.USER, BigDecimal.ZERO);
        mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "poor-xfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toUserId\": " + receiver.getId() + ", \"amount\": 300.00}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error", is("INSUFFICIENT_BALANCE")));
    }

    @Test
    void selfTransferRejected() throws Exception {
        String token = register("self@test.local", "Password123");
        Long selfId = userRepository.findByEmail("self@test.local").orElseThrow().getId();
        mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "self-xfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toUserId\": " + selfId + ", \"amount\": 10.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("SELF_TRANSFER")));
    }

    @Test
    void transferToNonexistentUserRejected() throws Exception {
        String token = register("ghost@test.local", "Password123");
        mockMvc.perform(post("/wallet/add")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "ghost-seed")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 100.00}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/wallet/transfer")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "ghost-xfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toUserId\": 999999, \"amount\": 10.00}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("USER_NOT_FOUND")));
    }

    @Test
    void userCannotAccessAdminWallets() throws Exception {
        String token = register("plainuser@test.local", "Password123");
        mockMvc.perform(get("/admin/wallets").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    void userCannotAccessAdminTransactions() throws Exception {
        String token = register("plainuser2@test.local", "Password123");
        mockMvc.perform(get("/admin/transactions").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        fixtures.createUser("boss@test.local", "Password123", Role.ADMIN, BigDecimal.ZERO);
        String adminToken = login("boss@test.local", "Password123");
        mockMvc.perform(get("/admin/wallets").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/admin/transactions").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void missingJwtRejected() throws Exception {
        mockMvc.perform(get("/wallet"))
            .andExpect(status().isUnauthorized());
    }
}
