package com.studyai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyai.auth.AuthService;
import com.studyai.auth.dto.AuthResponse;
import com.studyai.auth.dto.RegisterRequest;
import com.studyai.course.CourseRepository;
import com.studyai.course.dto.CourseRequest;
import com.studyai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    private String tokenStudentA;
    private String tokenStudentB;

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();
        userRepository.deleteAll();

        AuthResponse respA = authService.register(new RegisterRequest("Student A", "studentA@test.com", "Password123!"));
        tokenStudentA = respA.getToken();

        AuthResponse respB = authService.register(new RegisterRequest("Student B", "studentB@test.com", "Password123!"));
        tokenStudentB = respB.getToken();
    }

    @Test
    @DisplayName("1. Should successfully create a valid course (201 Created)")
    void shouldCreateCourseSuccessfully() throws Exception {
        CourseRequest request = new CourseRequest("Introduction à l'IA", "Concepts fondamentaux du Machine Learning");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Introduction à l'IA")))
                .andExpect(jsonPath("$.description", is("Concepts fondamentaux du Machine Learning")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("2. Should reject course creation with blank title (400 Bad Request)")
    void shouldRejectBlankTitle() throws Exception {
        CourseRequest request = new CourseRequest("   ", "Description sans titre");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title", notNullValue()));
    }

    @Test
    @DisplayName("3. Should list courses for the authenticated student only")
    void shouldListCoursesForAuthenticatedStudentOnly() throws Exception {
        // Student A creates a course
        CourseRequest requestA = new CourseRequest("Cours de Student A", "Description A");
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated());

        // Student A lists courses -> sees 1 course
        mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Cours de Student A")));

        // Student B lists courses -> sees 0 courses
        mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("4. Should get course details when owner requests it")
    void shouldGetCourseDetailsForOwner() throws Exception {
        CourseRequest requestA = new CourseRequest("Bases de données", "SQL & NoSQL");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(courseId.intValue())))
                .andExpect(jsonPath("$.title", is("Bases de données")));
    }

    @Test
    @DisplayName("5. Should update course when owner updates it")
    void shouldUpdateCourseForOwner() throws Exception {
        CourseRequest requestA = new CourseRequest("Titre Initial", "Description Initiale");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        CourseRequest updateRequest = new CourseRequest("Titre Modifié", "Description Modifiée");
        mockMvc.perform(put("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Titre Modifié")))
                .andExpect(jsonPath("$.description", is("Description Modifiée")));
    }

    @Test
    @DisplayName("6. Should delete course when owner deletes it (204 No Content)")
    void shouldDeleteCourseForOwner() throws Exception {
        CourseRequest requestA = new CourseRequest("Cours à supprimer", "Temporaire");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNoContent());

        // Verify it is gone
        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Should reject unauthenticated requests to /api/courses with 401 Unauthorized")
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CourseRequest("Titre", "Desc"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. Student B should get 404 when attempting to read Student A's course")
    void shouldDenyStudentBReadingStudentACourse() throws Exception {
        CourseRequest requestA = new CourseRequest("Cours Privé Student A", "Secret");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("9. Student B should get 404 when attempting to update Student A's course")
    void shouldDenyStudentBUpdatingStudentACourse() throws Exception {
        CourseRequest requestA = new CourseRequest("Cours Inviolable", "Sécurisé");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        CourseRequest hackRequest = new CourseRequest("Piraté", "Piraté");
        mockMvc.perform(put("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hackRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("10. Student B should get 404 when attempting to delete Student A's course")
    void shouldDenyStudentBDeletingStudentACourse() throws Exception {
        CourseRequest requestA = new CourseRequest("Cours Intouchable", "Protégé");
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());

        // Verify Student A can still access it
        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Cours Intouchable")));
    }
}
