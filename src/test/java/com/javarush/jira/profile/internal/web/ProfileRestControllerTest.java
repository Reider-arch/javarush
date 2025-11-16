package com.javarush.jira.profile.internal.web;


import com.javarush.jira.profile.ContactTo;
import com.javarush.jira.profile.ProfileTo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileRestController.class)
class ProfileRestControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @MockBean
    private AbstractProfileController abstractProfileController;

    // -----------------------------------------------------------
    // GET /api/profile
    // -----------------------------------------------------------

    @Test
    @WithMockUser(username = "user")
    void getProfile_success() throws Exception {
        ProfileTo profile = new ProfileTo(
                1L,
                Set.of("TASK_CREATED"),
                Set.of(new ContactTo("telegram", "@john"))
        );

        when(abstractProfileController.get(1L)).thenReturn(profile);

        mockMvc.perform(get("/api/profile")
                        .principal(() -> "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.mailNotifications[0]").value("TASK_CREATED"))
                .andExpect(jsonPath("$.contacts[0].code").value("telegram"))
                .andExpect(jsonPath("$.contacts[0].value").value("@john"));
    }

    @Test
    void getProfile_unauthorized() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void getProfile_serverError() throws Exception {
        when(abstractProfileController.get(1L))
                .thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(get("/api/profile")
                        .principal(() -> "1"))
                .andExpect(status().isInternalServerError());
    }

    // -----------------------------------------------------------
    // PUT /api/profile
    // -----------------------------------------------------------

    @Test
    @WithMockUser(username = "user")
    void updateProfile_success() throws Exception {
        doNothing().when(abstractProfileController).update(any(), eq(1L));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 1,
                                  "mailNotifications": ["TASK_CREATED"],
                                  "contacts": [
                                    {"code": "telegram", "value": "@john"}
                                  ]
                                }
                                """)
                        .principal(() -> "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user")
    void updateProfile_validationFail() throws Exception {
        // mailNotifications = null → @NotNull → 400 Bad Request
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 1,
                                  "contacts": []
                                }
                                """)
                        .principal(() -> "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_unauthorized() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void updateProfile_serverError() throws Exception {
        doThrow(new RuntimeException("Fail"))
                .when(abstractProfileController).update(any(), eq(1L));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 1,
                                  "mailNotifications": ["TASK_CREATED"],
                                  "contacts": [
                                    {"code": "telegram", "value": "@john"}
                                  ]
                                }
                                """)
                        .principal(() -> "1"))
                .andExpect(status().isInternalServerError());
    }
}
