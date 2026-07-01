// src/test/java/org/example/spring/TelegramInitDataFilterTest.java
package org.example.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramInitDataFilterTest {

    private static final String TEST_BOT_TOKEN = "123456789:test_token_for_unit_tests_only";
    private static final long TEST_USER_ID = 8032286461L;

    private TelegramInitDataFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TelegramInitDataFilter(TEST_BOT_TOKEN);
    }

    // --- Публичные пути ---

    @Test
    void publicPath_health_passesWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // цепочка продолжилась
    }

    @Test
    void publicPath_configJs_passesWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/config.js");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void publicPath_apiLanguage_passesWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/language");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // --- Защищённые пути без заголовка ---

    @Test
    void protectedPath_noHeader_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // цепочка прервалась
    }

    @Test
    void protectedPath_emptyHeader_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", "");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    // --- Неверные данные ---

    @Test
    void invalidInitData_garbage_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", "totally=garbage&data=here");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidInitData_tamperedHash_returns401() throws Exception {
        String validData = TestInitDataHelper.generateFreshInitData(TEST_BOT_TOKEN, TEST_USER_ID);
        // Подменяем hash на мусор
        String tampered = validData.replaceAll("hash=[a-f0-9]+", "hash=deadbeefdeadbeef");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", tampered);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidInitData_wrongBotToken_returns401() throws Exception {
        // Подписано другим токеном
        String dataSignedByOtherBot = TestInitDataHelper.generateFreshInitData(
            "999999999:wrong_bot_token", TEST_USER_ID
        );

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", dataSignedByOtherBot);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    // --- Устаревшие данные ---

    @Test
    void expiredInitData_returns401() throws Exception {
        String expiredData = TestInitDataHelper.generateExpiredInitData(TEST_BOT_TOKEN, TEST_USER_ID);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", expiredData);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    // --- Валидные данные ---

    @Test
    void validInitData_passes_andSetsChatId() throws Exception {
        String validData = TestInitDataHelper.generateFreshInitData(TEST_BOT_TOKEN, TEST_USER_ID);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Telegram-Init-Data", validData);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        // Проверяем что verified_chat_id установлен правильно
        assertThat(req.getAttribute("verified_chat_id")).isEqualTo(TEST_USER_ID);
    }
}