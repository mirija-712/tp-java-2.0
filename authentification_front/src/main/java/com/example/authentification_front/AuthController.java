package com.example.authentification_front;

import com.example.authentification_front.client.AuthApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;

/**
 * Contrôleur de la vue d'authentification (client lourd TP1).
 * Gère l'inscription, la connexion et l'affichage du profil (/api/me).
 * Cette implémentation est volontairement simplifiée et ne doit pas être utilisée en production.
 */
public class AuthController {

    @FXML
    private TextField registerEmail;
    @FXML
    private PasswordField registerPassword;
    @FXML
    private Label registerMessage;

    @FXML
    private TextField loginEmail;
    @FXML
    private PasswordField loginPassword;
    @FXML
    private Label loginMessage;

    @FXML
    private TitledPane profilPane;
    @FXML
    private Label profilInfo;
    @FXML
    private Label statusLabel;

    private AuthApiClient apiClient = new AuthApiClient();

    @FXML
    public void initialize() {
        clearMessage(registerMessage);
        clearMessage(loginMessage);
        statusLabel.setText("Prêt. Assurez-vous que le backend (authentification_back) tourne sur http://localhost:8080");
    }

    @FXML
    protected void onRegisterClick() {
        String email = registerEmail.getText() == null ? "" : registerEmail.getText().trim();
        String password = registerPassword.getText() == null ? "" : registerPassword.getText();
        clearMessage(registerMessage);

        if (email.isEmpty()) {
            setMessage(registerMessage, "L'email est requis.", false);
            return;
        }
        if (password.length() < 4) {
            setMessage(registerMessage, "Le mot de passe doit contenir au minimum 4 caractères.", false);
            return;
        }

        runAsync(() -> {
            var result = apiClient.register(email, password);
            Platform.runLater(() -> {
                if (result.success) {
                    setMessage(registerMessage, result.data, true);
                    registerPassword.clear();
                } else {
                    setMessage(registerMessage, result.errorMessage, false);
                }
            });
        });
    }

    @FXML
    protected void onLoginClick() {
        String email = loginEmail.getText() == null ? "" : loginEmail.getText().trim();
        String password = loginPassword.getText() == null ? "" : loginPassword.getText();
        clearMessage(loginMessage);

        if (email.isEmpty()) {
            setMessage(loginMessage, "L'email est requis.", false);
            return;
        }
        if (password.isEmpty()) {
            setMessage(loginMessage, "Le mot de passe est requis.", false);
            return;
        }

        runAsync(() -> {
            var result = apiClient.login(email, password);
            Platform.runLater(() -> {
                if (result.success) {
                    setMessage(loginMessage, result.data, true);
                    profilPane.setVisible(true);
                    refreshProfil();
                } else {
                    setMessage(loginMessage, result.errorMessage, false);
                }
            });
        });
    }

    @FXML
    protected void onRefreshMeClick() {
        refreshProfil();
    }

    @FXML
    protected void onLogoutClick() {
        apiClient = new AuthApiClient(); // Nouveau client = plus de cookies de session
        profilPane.setVisible(false);
        profilInfo.setText("");
        statusLabel.setText("Déconnecté.");
    }

    private void refreshProfil() {
        runAsync(() -> {
            var result = apiClient.getMe();
            Platform.runLater(() -> {
                if (result.success && result.data != null) {
                    var me = result.data;
                    profilInfo.setText("Id : " + me.getId() + " — Email : " + me.getEmail());
                    statusLabel.setText("Connecté. GET /api/me OK.");
                } else {
                    profilInfo.setText("Non connecté ou erreur : " + (result.errorMessage != null ? result.errorMessage : "?"));
                    statusLabel.setText(result.errorMessage != null ? result.errorMessage : "Erreur /api/me");
                }
            });
        });
    }

    private void setMessage(Label label, String text, boolean success) {
        label.setText(text);
        label.setStyle(success ? "-fx-text-fill: green;" : "-fx-text-fill: #c00;");
    }

    private void clearMessage(Label label) {
        label.setText("");
        label.setStyle("");
    }

    private void runAsync(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
