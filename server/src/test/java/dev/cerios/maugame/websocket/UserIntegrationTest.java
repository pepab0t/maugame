package dev.cerios.maugame.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootTest
@EnableWebMvc
public class UserIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoads() {
    }
}
