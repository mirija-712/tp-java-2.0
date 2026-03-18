package com.example.authentification;

import com.example.authentification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration pour l'API d'authentification.
 * <p>
 * Objectifs :
 * </p>
 * <ul>
 *     <li>vérifier que les endpoints REST exposés par {@code AuthController} et {@code MeController}
 *     se comportent comme décrit dans les sujets TP1/TP2 (codes HTTP, JSON de réponse),</li>
 *     <li>tester le flux complet de l'application via {@link MockMvc} :
 *     inscription, connexion, route protégée {@code /api/me},</li>
 *     <li>couvrir les cas d'erreur importants : email vide ou invalide, mot de passe trop court,
 *     email déjà utilisé, mauvais mot de passe, email inconnu, et verrouillage après 5 échecs.</li>
 * </ul>
 * <p>
 * Ces tests lancent un véritable contexte Spring Boot avec un profil {@code test} (base H2 en mémoire)
 * et simulent des requêtes HTTP comme si un client appelait l'API.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    /**
     * Teste l'inscription avec un email vide, doit retourner 400 Bad Request.
     */
    @Test
    void register_avecEmailVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    /**
     * Teste l'inscription avec un format d'email incorrect, doit retourner 400 Bad Request.
     */
    @Test
    void register_avecFormatEmailIncorrect_retourne400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Teste l'inscription avec un mot de passe trop court, doit retourner 400 Bad Request.
     */
    @Test
    void register_avecMotDePasseTropCourt_retourne400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    /**
     * Teste une inscription valide, doit retourner 200 OK.
     */
    @Test
    void register_valide_retourne200() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Teste l'inscription avec un email déjà existant, doit retourner 409 Conflict.
     */
    @Test
    void register_avecEmailExistant_retourne409() throws Exception {
        String body = "{\"email\":\"toto@test.com\",\"password\":\"Abcd1234!@#$\"}";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    /**
     * Teste une connexion valide, doit retourner 200 OK.
     */
    @Test
    void login_valide_retourne200() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@test.com\",\"password\":\"Abcd1234!@#$\"}"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@test.com\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Teste la connexion avec un mot de passe incorrect, doit retourner 401 Unauthorized.
     */
    @Test
    void login_motDePasseIncorrect_retourne401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"Abcd1234!@#$\"}"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"wrongpwd\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * Teste la connexion avec un email inconnu, doit retourner 401 Unauthorized.
     */
    @Test
    void login_emailInconnu_retourne401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@test.com\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * Teste la connexion après 5 échecs consécutifs, doit retourner 423 Locked.
     */
    @Test
    void login_apresCinqEchecs_retourne423() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"locked@test.com\",\"password\":\"Abcd1234!@#$\"}"));

        // 5 tentatives avec mauvais mot de passe
        String badBody = "{\"email\":\"locked@test.com\",\"password\":\"Wrong123!\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badBody))
                    .andExpect(i < 4 ? status().isUnauthorized() : status().isLocked());
        }
    }

    /**
     * Teste l'accès à /api/me sans authentification, doit retourner 401 Unauthorized.
     */
    @Test
    void me_sansAuthentification_retourne401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Teste l'accès à /api/me après connexion, doit retourner 200 OK avec les données utilisateur.
     */
    @Test
    void me_apresLogin_retourne200etDonneesUtilisateur() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@test.com\",\"password\":\"Abcd1234!@#$\"}"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@test.com\",\"password\":\"Abcd1234!@#$\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@test.com"))
                .andExpect(jsonPath("$.id").exists());
    }
}
