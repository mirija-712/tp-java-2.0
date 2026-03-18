package com.example.authentification_front;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Point d'entrée JavaFX pour le client lourd.
 * <p>
 * Charge le fichier FXML {@code auth-view.fxml}, applique la feuille de style {@code auth.css}
 * et affiche la fenêtre principale avec le titre de l'application.
 * </p>
 */
public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("auth-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 400, 580);
        scene.getStylesheets().add(HelloApplication.class.getResource("auth.css").toExternalForm());
        stage.setTitle("Authentification TP1 – Client lourd");
        stage.setScene(scene);
        stage.show();
    }
}
